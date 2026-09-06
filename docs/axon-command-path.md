# コマンドが流れる経路（Axon 実装ウォークスルー）

> **この文書の位置づけ**: 実装した垂直スライスが Axon の中で実際にどう動くかを、
> クラス・メソッド・行まで辿れる形で残した**読み物**。決定ではないので理由は書かない
> （決定の正は [`decisions.md`](decisions.md)、仕様の正は [`tactical-design.md`](tactical-design.md)）。
>
> **前提のバージョン**: Axon Framework 4.13.2（行番号は sources jar のもの）/ 本PoCは M3-a の
> 最初の垂直スライス時点。**4.x → 5.x へ上げたら Axon 側の行番号は当てにならない**——
> 構造の説明は残るが、行は都度引き直すこと。
>
> **使い方**: M3 の実装を再開する前に読む。末尾の[自己確認の問い](#自己確認の問い)で理解を点検してから次へ進む。

## 先に要点3つ

1. **集約クラスはコマンドの型で決まる。** `@TargetAggregateIdentifier` は「どの集約クラスか」には
   関与せず、「**どのインスタンスか**」を表す値を差し出すだけ。
2. **識別子は2か所で独立に `toString()` される**（ロードのキー / 書き出すイベントに刻む ID）。
   型システムは両者の一致を何も保証しない。だから識別子の値オブジェクトは `toString()` を上書きする。
3. **`@EventSourcingHandler` は復元でも `apply()` でも同じ経路で呼ばれる**（`AnnotatedAggregate:378`）。
   ここに副作用を書けないのは、集約を読み込むたびに再実行されるから
   （[`.claude/rules/event-sourcing.md`](../.claude/rules/event-sourcing.md)）。

---

## 俯瞰

`POST /api/receipts/RCP-1/putaways` を例に取る（入荷 `RCP-1` は 50 受入・30 格納済み、そこへ 20 の格納）。

```
sendAndWait(PutAwayStock)
  ① コマンド型でハンドラを引く      → 集約クラス = InboundReceipt   （Phase 2）
  ② @TargetAggregateIdentifier      → 値 "RCP-1"（toString ①か所目）（Phase 3）
  ③ "RCP-1" のストリームを読んで復元 → @EventSourcingHandler         （Phase 4）
  ④ handle(PutAwayStock) を実行     → apply(...)（toString ②か所目）（Phase 5）
  ⑤ UnitOfWork のコミット           → 追記 → 発行 → ポリシーへ       （Phase 6）
```

---

## Phase 0 — 起動時（ここで「表」ができる）

| # | 呼び出し | 場所 |
|---|---|---|
| 0-1 | `AggregateConfigurer.defaultConfiguration(InboundReceipt.class)` を Bean 登録 | `warehouse-app/.../app/AxonConfiguration.java` |
| 0-2 | クラスを走査して `@CommandHandler` を集め、`AggregateAnnotationCommandHandler` を構築。`supportedCommandNames` に3件（`ReceiveStock` / `PutAwayStock` / `CloseInboundReceipt`）が入る | `AggregateAnnotationCommandHandler:111,123,224` |
| 0-3 | `subscribe(CommandBus)` がコマンド名ごとに `commandBus.subscribe(name, handler)` を呼ぶ | `AggregateAnnotationCommandHandler:147-152` → `SimpleCommandBus:212-` |

**この時点で「コマンド名 → ハンドラ」の Map が完成する。** 以降、型から集約クラスを探す処理は走らない。
登録し忘れた集約は、`@CommandHandler` が付いていてもどこにも購読されず `NoHandlerForCommandException` になる。

`@Aggregate` を使わずアプリ層で登録しているのは、`warehouse-domain` を Spring に依存させないため
（[H33](decisions.md#h33-アプリケーションアーキテクチャ)）。

0-2 でハンドラの種類が3つに分かれる（`initializeHandlers`:163）。

| 集約側の書き方 | 生成されるハンドラ | 本PoCの該当 |
|---|---|---|
| コンストラクタ `@CommandHandler` | `AggregateConstructorCommandHandler`（`:457-458`。`repository.newInstance`） | `ReceiveStock` |
| 通常メソッド | `AggregateCommandHandler`（`:587-589`） | `PutAwayStock` / `CloseInboundReceipt` |
| `@CreationPolicy(CREATE_IF_MISSING)` | `AggregateCreateOrUpdateCommandHandler`（`:502-518`） | `PlaceStock` |

**識別子の出所が違えば誕生の作り方も違う**——入荷IDは外から与えられ、在庫IDは棚に初めて物が入った瞬間に確定する。

---

## Phase 1 — HTTP からコマンドまで

```
POST /api/receipts/RCP-1/putaways  {"locationId":"A-01","quantity":20}
  → ReceivingController#putAway            warehouse-app/.../app/receiving/ReceivingController.java
      new PutAwayStock(new ReceiptId("RCP-1"), new LocationId("A-01"), new Quantity(20))
  → DefaultCommandGateway#sendAndWait      DefaultCommandGateway:98
  → AbstractCommandGateway#send            AbstractCommandGateway:70
  → SimpleCommandBus#dispatch              SimpleCommandBus:122
```

`sendAndWait` は内部の `CompletableFuture` を待つだけで、実行は呼び出しスレッドで進む（`SimpleCommandBus` は同期）。
コマンドを同期にしている理由は [H38](decisions.md#h38-受入ステップが叩く-rest-api-の契約)。

---

## Phase 2 — ①コマンド型でハンドラを引く

```
SimpleCommandBus#doDispatch                SimpleCommandBus:159-172
  findCommandHandlerFor(command)           :175   ← 0-3 で作った Map を引く
    見つからなければ NoHandlerForCommandException（:166-171）
SimpleCommandBus#handle                    SimpleCommandBus:188-201
  DefaultUnitOfWork.startAndGet(command)   :196   ← ここで UnitOfWork 開始（Phase 6 で効く）
  → AggregateAnnotationCommandHandler#handle       :229-235
      handlers から canHandle で1つ選ぶ → AggregateCommandHandler
```

**ここで集約クラスは確定している。** `ReceiptId` という型は一度も参照されていない。

---

## Phase 3 — ②`@TargetAggregateIdentifier` から値を取る

```
AggregateCommandHandler#handle                       AggregateAnnotationCommandHandler:587-590
  commandTargetResolver.resolveTarget(command)       AnnotationCommandTargetResolver:88-111
    findIdentifier → invokeAnnotated(command, TargetAggregateIdentifier.class)
                                                     :114-115, :121-130
      → PutAwayStock#receiptId() を反射で呼ぶ         warehouse-domain/.../receiving/PutAwayStock.java
      → ReceiptId("RCP-1") というオブジェクトが返る
    注釈が1つも無ければ IllegalArgumentException     :103-110
  new VersionedAggregateIdentifier(識別子, version)   :111
```

**文字列化の1か所目**:

```java
// VersionedAggregateIdentifier:68-70
public String getIdentifier() {
    return identifier.toString();     // ← ReceiptId#toString() = "RCP-1"
}
```

型は見ていないので、識別子が `String` でも `UUID` でも動く。逆に言えば**型が合っていることは何の保証にもならない**。

---

## Phase 4 — ③ストリームを読んで復元

```
repository.load("RCP-1", null)                       AggregateAnnotationCommandHandler:589
  AbstractRepository#load                            AbstractRepository:141-165
    managedAggregates(uow) に無ければ doLoad         :150-158
  LockingRepository#doLoad → ロック取得 → doLoadWithLock
  EventSourcingRepository#doLoadWithLock             EventSourcingRepository:129-144
    readEvents("RCP-1")                              :170-171 → eventStore.readEvents("RCP-1")
    ストリームが空なら AggregateNotFoundException     :132-133
    doLoadAggregate                                  :146-160
      EventSourcedAggregate.initialize(空インスタンス)  ← protected InboundReceipt() を使う
      loadingAggregate.initializeState(eventStream)  :158
        EventSourcedAggregate#initializeState        EventSourcedAggregate:292-303
          initializing = true
          eventStream.forEachRemaining(this::publish)    ← イベントを1件ずつ
            AnnotatedAggregate#publish               AnnotatedAggregate:374-380
              lastKnownSequence = event の seq       :376
              inspector.publish(msg, aggregateRoot)  :378 ← @EventSourcingHandler を呼ぶ
                 → InboundReceipt#on(StockReceived)      （receivedQty=50, putAwayQty=0）
                 → InboundReceipt#on(StockPutAway)       （putAwayQty=30）
              publishOnEventBus(msg)                 :379
                 EventSourcedAggregate がオーバライド → initializing 中は何もしない
                                                     EventSourcedAggregate:275-281
```

**状態は保存されていないので、これが毎コマンド走る。** イベントが100件あれば100回呼ばれる
（スナップショットは最適化としてのみ後から入れる余地がある）。

`initializing` フラグが「**復元中は外へ出さない**」の実装。復元と `apply()` の違いは `:379` の先だけで、
`:378`（`@EventSourcingHandler` の呼び出し）は両方から通る。

---

## Phase 5 — ④コマンドハンドラ実行と apply

```
aggregate.handle(command)                            AggregateAnnotationCommandHandler:589 の続き
  AnnotatedAggregate#handle(CommandMessage)          AnnotatedAggregate:394-399, 428-450
  → InboundReceipt#handle(PutAwayStock)              warehouse-domain/.../receiving/InboundReceipt.java
       受付ゲート（数量ゼロ / クローズ済み / 残格納量超過）
         → 例外ならここで終了。apply に到達しないのでイベントは1件も書かれない
       apply(new StockPutAway(...))
         AggregateLifecycle.apply → AnnotatedAggregate#doApply    AnnotatedAggregate:459-479
           applying=true → createMessage(payload)    :492-505
             long seq = lastKnownSequence + 1        :495-496
             String id = identifierAsString();       :497   ← 文字列化の2か所目
             new GenericDomainEventMessage<>(type, id, seq, payload, metaData)   :503
           publish(msg)                              :463 → AnnotatedAggregate:374-380
             inspector.publish → on(StockPutAway)    （putAwayQty=50 になる）
             publishOnEventBus → eventBus.publish    :389（initializing=false なので通る）
       if (receivedQty.equals(putAwayTotal))
       apply(new InboundReceiptClosed(...))          ← 同じ経路で seq+1 の2件目（H14）
```

**文字列化の2か所目**:

```java
// Aggregate:53-55
default String identifierAsString() {
    return Objects.toString(identifier(), null);   // ← @AggregateIdentifier フィールドの toString()
}
```

この値は誕生イベントのハンドラで入る。**入れ忘れると** `EventSourcedAggregate:263-266` の
`IncompatibleAggregateException("Aggregate identifier must be non-null after applying an event.")` になる。

`apply()` が同期に `@EventSourcingHandler` を呼ぶので、1つ目の apply の直後に `putAwayQty` は更新済み。
**2つ目の apply（完了クローズ）が更新後の状態を見られるのはこのため**。

---

## ②と④が食い違うと何が起きるか

```
② VersionedAggregateIdentifier#getIdentifier()   → コマンド側の値の toString  → ストリームを読むキー
④ Aggregate#identifierAsString()                 → 集約側フィールドの toString → 書き出すイベントに刻む ID
```

**別々のオブジェクトに対する別々の `toString()` 呼び出し**であり、両者の一致は誰も保証していない。
片方だけレコード既定の表現だと「`RCP-1` のストリームを読んで `ReceiptId[value=RCP-1]` のストリームへ書く」が成立する。
これが、識別子の値オブジェクトで `toString()` を上書きしている理由（`ReceiptId` / `InventoryItemId`）。

実装中にこれを捕まえたのは**テスト Fixture**だった。

```java
// AggregateTestFixture:1023-1029
if (!lastEvent.getAggregateIdentifier().equals(event.getAggregateIdentifier())) {
    throw new EventStoreException("Writing events for an unexpected aggregate. ...");
}
```

この文字列は **Axon 本体には存在せず `axon-test` だけにある**。本番経路で同じズレが起きた場合は
別の壊れ方（`AggregateNotFoundException`、あるいは seq 0 から書き直して楽観ロック違反）をする。
**Fixture のほうが本番より厳しくズレを検出してくれる**、という関係になっている。

---

## `PlaceStock`（在庫）だけ Phase 3〜4 が違う

`CREATE_IF_MISSING` は分岐する側のハンドラ。

```
AggregateCreateOrUpdateCommandHandler#handle          AggregateAnnotationCommandHandler:502-518
  resolveNullableAggregateId(command)                 :526-533（識別子が無ければ null 許容）
  repository.loadOrCreate("SKU-A@A-01", factory)      :507-510
    AbstractRepository#loadOrCreate                   AbstractRepository:167-
      → 有れば doLoad（Phase 4 と同じ）／無ければ doCreateNew（:113-125）
  instance.handle(command)                            :511
  → InventoryItem#handle(PlaceStock)                  warehouse-domain/.../inventory/InventoryItem.java
```

キーになる `"SKU-A@A-01"` は `InventoryItemId#toString()` → `asString()`。
複合識別子を単一の文字列に畳んで集約識別子にする形の実体がここ（[H43](decisions.md#h43-bc-をまたぐ識別子の帰属)）。

---

## Phase 6 — コミット（UnitOfWork）

`SimpleCommandBus:196` で始めた UnitOfWork が確定するとき:

1. `AbstractRepository#prepareForCommit` 経由で未確定イベントをイベントストアへ**追記**
   （同一コマンドの複数イベントは同じトランザクション ＝ ES ルールの原子性）
2. その後イベントバスへ**発行** → `PutawayPolicy#on(StockPutAway)` が別トランザクションで動く（格納伝播（P1））
3. `sendAndWait` が戻り、HTTP 201 が返る

**追記が先で発行が後**。イベントストアに載っていない出来事は誰にも届かない＝
「真実の源泉はイベントストアだけ」がこの順序で担保されている。
発行側（入荷集約）は購読側（ポリシー）を知らず、繋がりは `@EventHandler` の第1引数の型だけ。

---

## 自己確認の問い

読み返しの点検用。答えられなければ本文の該当節へ戻る。

1. `PutAwayStock` が `InboundReceipt` に届くのは何が決めているか。`@TargetAggregateIdentifier` の**型**は関係するか（Phase 0・2・3）
2. 識別子が文字列化される2か所はどこか。なぜ型システムが一致を保証できないのか（Phase 3・5）
3. `@EventSourcingHandler` はいつ呼ばれるか。「コマンド1回につき1回」ではないのはなぜか（Phase 4・5）
4. `@EventSourcingHandler` に外部呼び出しを書くと何が起きるか（要点3）
5. 受付ゲートで例外を投げたとき、イベントストアには何が書かれるか（Phase 5）
6. `InboundReceipt`（コンストラクタ）と `InventoryItem`（`CREATE_IF_MISSING`）で誕生の作り方が違うのはなぜか（Phase 0 の表）
7. 入荷集約はポリシーを呼んでいないのに、なぜ在庫が計上されるのか。追記と発行の順序はどちらが先か（Phase 6）
