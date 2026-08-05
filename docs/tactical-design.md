# 戦術設計（M2） — 倉庫在庫管理

> 前段: [`event-storming/`](event-storming/)（M1 戦略設計）。この文書の狙いは、分析で確定した集約・コマンド・
> イベント・不変条件を **型レベル（フィールド・シグネチャ・受付ゲート・例外）まで落とす**こと。実装（M3）は
> この文書を仕様として TDD で駆動する。用語の正は [`ubiquitous-language.md`](ubiquitous-language.md)、
> **決定の理由の正は [`decisions.md`](decisions.md)**（この文書は決定の*結果*だけを書く）。
>
> **進め方**: 集約ごとのスライスで確定していく。コア（在庫）→ 入荷 → 出荷 → 棚卸。
>
> | スライス | 状態 |
> |---|---|
> | ① 在庫（`InventoryItem`）★コア | **確定**（2026-08-03） |
> | ② 入荷（`InboundReceipt`） | **確定**（2026-08-05） |
> | ③ 出荷（`Shipment`） | 未着手 |
> | ④ 棚卸（`Stocktake`） | 未着手 |
> | ⑤ ポリシー P1〜P5 / リードモデル | 未着手 |

## 表記の約束
- 主表現は日本語。英名はコード識別子として併記する（[`event-storming/00-method.md`](event-storming/00-method.md) の表記の約束に従う）。
- コードブロックは Java の `record` 前提（Java 25）。`@AggregateIdentifier` 等の Axon アノテーションは M3 実装時に付ける。
- 「受付ゲート」= `@CommandHandler` で判定し、違反時は**例外を投げてイベントを発行しない**（[`.claude/rules/aggregate-design.md`](../.claude/rules/aggregate-design.md)）。

---

## 共通の値オブジェクト

原始型の乱用を避ける（[`.claude/rules/ddd-ubiquitous-language.md`](../.claude/rules/ddd-ubiquitous-language.md)）。

| 日本語 | 英名 | 中身 | 不変条件・振る舞い |
|---|---|---|---|
| SKU | `Sku` | 文字列 | 非空 |
| ロケーション | `LocationId` | 文字列 | 非空 |
| 在庫ID | `InventoryItemId` | `Sku` × `LocationId` | 複合。文字列化して集約識別子にする |
| 数量 | `Quantity` | 非負整数 | `≥ 0`。加算・減算（負になるなら例外）・比較・`ZERO` |
| 数量差分 | `QuantityDelta` | **符号付き**整数 | 棚卸調整の差分専用。増減の向きを持つ |
| 引当ID | `AllocationId` | 文字列 | 非空。引当1件を指す |
| 入荷ID | `ReceiptId` | 文字列 | 非空 |
| 出荷ID | `ShipmentId` | 文字列 | 非空 |
| 棚卸ID | `StocktakeId` | 文字列 | 非空 |

```java
public record Quantity(int value) {
    public Quantity {
        if (value < 0) throw new IllegalArgumentException("数量は非負でなければならない: " + value);
    }
    public static final Quantity ZERO = new Quantity(0);
    public Quantity plus(Quantity other)  { return new Quantity(value + other.value); }
    public Quantity minus(Quantity other) { return new Quantity(value - other.value); } // 負なら例外
    public boolean isAtLeast(Quantity other) { return value >= other.value; }
    public boolean isZero() { return value == 0; }
}
```

> `Quantity` を非負に閉じるため、**引当可能（`available`）は `Quantity` で表さない**（H12 により負を取りうる）。
> 集約内では `int` の導出計算とし、判定は「引当可能 ≥ 要求量」の形でのみ行う。

### 集約識別子の表現（`InventoryItemId`）

在庫集約の識別子は `(Sku, LocationId)` の複合だが、Axon の `@AggregateIdentifier` は**単一の識別子**を要求する。
そこで複合を値オブジェクトに閉じ込め、**文字列化したものを集約識別子とする**。

```java
public record InventoryItemId(Sku sku, LocationId locationId) {
    public String asString() { return sku.value() + "@" + locationId.value(); } // 例: SKU-1234@A-01-03
    public static InventoryItemId parse(String s) { /* "@" で分割 */ }
}
```

- 分離子 `@` は `Sku` / `LocationId` に現れない前提を**値オブジェクトの検証で保証**する（含んでいたら生成時に例外）。
- リードモデルでは `sku` / `locationId` を**別カラムに分けて**持つ（SKU横断・ロケーション横断の検索が要るため）。文字列IDは結合キーとしてのみ使う。

---

## 集約① 在庫（`InventoryItem`）★コア

**責務**: SKU × ロケーション（棚1マス）の**残高と、それを守る約束**。
物そのものではなく「引当可能を負にしない」という約束の責任者。

### 状態

```
InventoryItem
  id          : InventoryItemId          // 集約識別子
  onHand      : Quantity                 // 手持在庫（物理的に手元にある数量）
  allocations : Map<AllocationId, Quantity>   // 引当明細
  frozen      : boolean                  // 凍結中（棚卸対象）
  frozenBy    : StocktakeId | null       // どの棚卸で凍結されたか

  // 導出値（フィールドに持たない）
  allocated = allocations.values().sum()
  available = onHand - allocated         // ★ 負を取りうる（H12）
```

- **引当は明細で持つ**（合計だけではない）。理由: 解除・払出が「どの引当か」を指せないと、
  二重解除・引当量を超える払出・存在しない引当の解除を集約が拒否できないため。
  引当済（`allocated`）は明細の合計＝**導出値**。
- **`frozenBy` を持つ理由**: 解凍要求が「凍結した棚卸と同じか」を照合し、別の棚卸による誤解凍を防ぐため。

### 不変条件と、その強制点

| 不変条件 | 強制点 | 備考 |
|---|---|---|
| **引当可能 = 手持在庫 − 引当済 ≥ 0** | `AllocateStock` の受付ゲート（`引当可能 ≥ 要求量`） | コアの約束。**棚卸調整のみが例外的に負を持ち込む**（H12） |
| 手持在庫 ≥ 0 | `IssueStock` の受付ゲート（`手持在庫 ≥ 払出量`） | H12 の負状態では引当量の確認だけでは足りない（下記） |
| 引当明細の各数量 ≥ 0 | `DeallocateStock` / `IssueStock` の受付ゲート | 引当量を超える解除・払出を拒否 |
| 凍結中は物理を動かすコマンドを拒否 | `PlaceStock` / `IssueStock` の受付ゲート | 実地値を狂わせないため |

> **なぜ「状態として ≥ 0」ではなく「受付ゲート」なのか（H12）**
> 棚卸調整（`AdjustStock`）だけが引当可能を負にしうる唯一の経路で、この調整は必ず通す
> （例: 手持在庫50・引当済45 の棚を数えたら42 → 引当可能 −3）。負に落ちても新規引当は自動的に全部止まる。
> **判断の理由・却下した選択肢（拒否案／自動解除案）は [`decisions.md`](decisions.md#h12-実地値が引当済を下回る棚卸調整) を参照。**

### 凍結中（`frozen`）の受付可否

| コマンド | 凍結中 | 理由 |
|---|---|---|
| 在庫を計上する（`PlaceStock`） | **✕ 拒否** | 物理が動く＝実地値が狂う |
| 在庫を払い出す（`IssueStock`） | **✕ 拒否** | 物理が動く＝実地値が狂う |
| 引き当てる（`AllocateStock`） | ○ 通す | 物理は動かない（H11。露出は P2 が引当先を後回しにして下げる） |
| 引当を解除する（`DeallocateStock`） | ○ 通す | 物理は動かない |
| 在庫を調整する（`AdjustStock`） | ○ 通す | 棚卸のためのコマンドそのもの |
| 凍結する／解凍する | ○ 通す | 凍結制御そのもの（冪等） |

### コマンド → 受付ゲート → イベント → 状態遷移

#### 1. 在庫を計上する（`PlaceStock`）— 起点: ポリシー P1（格納伝播）

```java
record PlaceStock(InventoryItemId inventoryItemId, Quantity quantity, ReceiptId receiptId)
record StockPlaced(InventoryItemId inventoryItemId, Quantity quantity, ReceiptId receiptId)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 数量がゼロ | `InvalidQuantityException` |
| 凍結中 | `InventoryFrozenException` |

- **集約の誕生**: `@CreationPolicy(CREATE_IF_MISSING)`。その棚マスに初めて物が入った瞬間に集約が生まれる
  （分析の「格納で誕生」に忠実。棚マスタ登録という概念をドメインに増やさない）。
- 状態遷移: `onHand += quantity`

#### 2. 引き当てる（`AllocateStock`）— 起点: ポリシー P2（引当）★コア

```java
record AllocateStock(InventoryItemId inventoryItemId, AllocationId allocationId, Quantity quantity)
record StockAllocated(InventoryItemId inventoryItemId, AllocationId allocationId, Quantity quantity)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 数量がゼロ | `InvalidQuantityException` |
| 引当IDが既知（二重引当） | `DuplicateAllocationException` |
| **引当可能 < 要求量** | **`InsufficientAvailableStockException`** ← コアの不変条件 |

- 凍結中でも**通す**（H11）。
- 状態遷移: `allocations.put(allocationId, quantity)`

#### 3. 引当を解除する（`DeallocateStock`）— 起点: 取消・期限切れ

```java
record DeallocateStock(InventoryItemId inventoryItemId, AllocationId allocationId, DeallocationReason reason)
record StockDeallocated(InventoryItemId inventoryItemId, AllocationId allocationId,
                        Quantity quantity, DeallocationReason reason)
enum DeallocationReason { ORDER_CANCELLED, EXPIRED }   // 注文取消 / 期限切れ
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 引当IDが未知（二重解除・誤ID） | `UnknownAllocationException` |

- **全量解除に限る**（部分解除の要求はない。必要になれば M3+ で足す）。イベントには解除された数量を載せる
  （リードモデルがイベント単独で更新できるように＝プロジェクションが集約の状態を引かない）。
- 凍結中でも通す。
- 状態遷移: `allocations.remove(allocationId)`

#### 4. 在庫を払い出す（`IssueStock`）— 起点: ポリシー P3（出庫反映）

```java
record IssueStock(InventoryItemId inventoryItemId, AllocationId allocationId, Quantity quantity)
record StockIssued(InventoryItemId inventoryItemId, AllocationId allocationId, Quantity quantity)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 数量がゼロ | `InvalidQuantityException` |
| 凍結中 | `InventoryFrozenException` |
| 引当IDが未知 | `UnknownAllocationException` |
| 払出量 > その引当の残量 | `IssueExceedsAllocationException` |
| **払出量 > 手持在庫** | **`InsufficientOnHandException`** ← H12 の帰結 |

- **部分払出を許す**（ピッキングで一部しか取れないことは現実に起きる）。残りは引当のまま。
- **最後のゲートが要る理由（H12）**: 通常は 引当済 ≤ 手持在庫 なので引当量の確認だけで足りるが、
  棚卸調整で引当可能が負に落ちた状態では 引当済 > 手持在庫 となり、引当量の範囲内でも手持在庫を負にしうる。
- 状態遷移: `onHand -= quantity`／該当引当を減算（ゼロになったら明細から除去）。
  **手持在庫と引当済が同額ずつ減るので引当可能は不変**（H7）。

#### 5. 在庫を調整する（`AdjustStock`）— 起点: ポリシー P4（棚卸反映）

```java
record AdjustStock(InventoryItemId inventoryItemId, Quantity countedQuantity, StocktakeId stocktakeId)
record StockAdjusted(InventoryItemId inventoryItemId, QuantityDelta delta,
                     Quantity countedQuantity, StocktakeId stocktakeId)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 別の棚卸が凍結中（`凍結中 かつ frozenBy ≠ stocktakeId`） | `NotFrozenByThisStocktakeException` |

- **数量については拒否条件を持たない**のがこのコマンドの特徴。凍結中でも通し、引当可能が負になっても通す。
- 拒否するのは**呼び出し元の正当性**のみ（他の棚卸が凍結中の棚＝同じ棚を2つの棚卸が同時に触っている）。
  **凍結されていない場合は通す**（凍結漏れを理由に数えた事実を捨てない）。
- 理由は [`decisions.md`](decisions.md#h12-実地値が引当済を下回る棚卸調整)（H12）と
  [`decisions.md`](decisions.md#h11-棚卸中の引当をどこまで許すか)（H11「既知のリスク」＝凍結漏れの許容）。
- `delta = 実地値 − 手持在庫`（符号付き）。**差分ゼロならイベントを発行しない**
  （何も起きていないため。「数えた」という事実は棚卸集約の `StockCounted` に残るので情報は失われない）。
- イベントは**符号付き差分＋実地値＋原因**を持つ（H10）。過去イベントを書き換えない**補正**イベント。
  帳簿値は `実地値 − 差分` で復元できる。
- 状態遷移: `onHand = countedQuantity`

#### 6-7. 凍結する／解凍する（`FreezeStock` / `UnfreezeStock`）— 起点: サーガ P5

```java
record FreezeStock(InventoryItemId inventoryItemId, StocktakeId stocktakeId)
record StockFrozen(InventoryItemId inventoryItemId, StocktakeId stocktakeId)

record UnfreezeStock(InventoryItemId inventoryItemId, StocktakeId stocktakeId)
record StockUnfrozen(InventoryItemId inventoryItemId, StocktakeId stocktakeId)
```

| コマンド | 挙動 |
|---|---|
| `FreezeStock` | 既に同じ棚卸で凍結中なら**イベントを発行しない**（冪等）。別の棚卸で凍結中なら `AlreadyFrozenException` |
| `UnfreezeStock` | 凍結中でなければ**イベントを発行しない**（冪等）。凍結した棚卸と違えば `NotFrozenByThisStocktakeException` |

- **冪等にする理由**: 発行元が Saga（P5）であり、再送・再起動での重複送信がありうるため。
  「同じ結果になる要求は黙って受け入れ、矛盾する要求は拒否する」で分ける。

### 例外一覧

| 例外 | 意味 |
|---|---|
| `InsufficientAvailableStockException` | 引当可能 < 要求量。**コアの不変条件違反** |
| `InsufficientOnHandException` | 払出量 > 手持在庫（H12 の負状態でのみ到達） |
| `IssueExceedsAllocationException` | 払出量 > その引当の残量 |
| `DuplicateAllocationException` | 同じ引当IDでの二重引当 |
| `UnknownAllocationException` | 未知の引当IDの解除・払出 |
| `InventoryFrozenException` | 凍結中に物理を動かすコマンド（計上・払出） |
| `AlreadyFrozenException` / `NotFrozenByThisStocktakeException` | 別の棚卸による凍結・解凍・調整（棚の取り合い） |
| `InvalidQuantityException` | 数量ゼロ等、事実として成立しない数量 |

### テスト骨子（Axon `AggregateTestFixture` / Given-When-Then）

M3 で**先に書いて赤にする**（[`.claude/rules/testing.md`](../.claude/rules/testing.md)）。ドメインの言葉で書く。

**正常系**
1. 空の棚に在庫を計上すると、在庫が計上され手持在庫が増える（集約が誕生する）
2. 引当可能の範囲で引き当てられる
3. 引当を解除すると引当可能が戻る
4. 払い出すと手持在庫と引当済が同額ずつ減り、**引当可能は変わらない**
5. 一部だけ払い出すと、残りは引当のまま残る
6. 実地値が帳簿値を下回ると、符号付き差分を持つ調整イベントが出て手持在庫が実情に合う

**不変条件（異常系・`expectException` でイベントを発行しないこと）**
7. 引当可能を超える引当は拒否される ★コア
8. 同じ引当IDでの二重引当は拒否される
9. 未知の引当IDの解除は拒否される
10. 未知の引当IDの払出は拒否される
11. 引当量を超える払出は拒否される
12. **手持在庫を超える払出は拒否される**（H12 の帰結）
13. 数量ゼロの計上・引当・払出は拒否される

**凍結（P5）**
14. 凍結中の計上は拒否される
15. 凍結中の払出は拒否される
16. **凍結中でも引当は通る**（H11）
17. 同じ棚卸での二重凍結はイベントを発行しない（冪等）
18. 別の棚卸が凍結中の在庫への凍結要求は拒否される
19. 凍結した棚卸と異なる棚卸からの解凍要求は拒否される
20. 凍結中でない在庫への解凍要求はイベントを発行しない（冪等）

**境界・H12**
21. 差分ゼロの調整はイベントを発行しない
22. 実地値が帳簿値を上回る調整では、正の差分を持つ調整イベントが出る
23. **実地値が引当済を下回っても調整は通り、以降の新規引当がすべて拒否される**（H12）
24. 別の棚卸が凍結中の在庫への調整は拒否される
25. 凍結されていない在庫への調整は通る（凍結漏れがあっても数えた事実は捨てない）

---

## 集約② 入荷（`InboundReceipt`）

**責務**: 受入ドックに置かれた**1SKU の物のかたまり**と、それを守る約束。
「受け入れた量より多くを棚に上げない」（格納累計 ≤ 受入量）の責任者。
在庫と違い**引当には関与しない**（ロケーション未確定の在庫は引けない＝ H3 / H6）。

- 検品は独立させず受入〜格納に**内包**する（H8）。`InspectStock` / `StockInspected` は作らない。
- **1入荷 = 1SKU**。`ReceiptId` は伝票番号ではなく**ドックに置かれた物のかたまりの識別子**
  （H13 の帰結。ユニットは代替可能なので、かたまりを分ける軸は SKU だけでよい）。

### 状態

```
InboundReceipt
  id          : ReceiptId       // 集約識別子
  sku         : Sku             // 1入荷 = 1SKU
  receivedQty : Quantity        // 受入量（受入後は不変）
  putAwayQty  : Quantity        // 格納累計
  closed      : boolean         // クローズ済み（H14）
  closureReason : ClosureReason | null

  // 導出値（フィールドに持たない）
  remaining = receivedQty - putAwayQty   // 残格納量。★ 常に ≥ 0
```

- **残格納量を持たず格納累計を持つ**理由: 在庫側で引当済を明細の合計＝導出値にしたのと同じ流儀
  （積み上げた事実を保持し、残りは引き算で出す）。イベント単独でリードモデルも更新できる。
- **残格納量は `Quantity`（非負）で表せる**。在庫の引当可能（負を取りうる／H12）との違いは、
  **負を持ち込む経路がここには無い**こと。棚卸調整に相当する「外から実情を上書きする入力」が入荷には無く、
  変化はすべて自分の受付ゲートを通った格納だけで起きる。

### 不変条件と、その強制点

| 不変条件 | 強制点 | 備考 |
|---|---|---|
| **格納累計 ≤ 受入量**（残格納量 ≥ 0） | `PutAwayStock` の受付ゲート（`残格納量 ≥ 格納量`） | 受け入れた以上を棚に上げない |
| クローズ後は格納しない | `PutAwayStock` の受付ゲート | 終わった入荷は動かない（H14） |
| 受入量は受入後に変わらない | 変更コマンドを持たない | 訂正が要るなら打ち切って入荷し直す |

### コマンド → 受付ゲート → イベント → 状態遷移

#### 1. 入荷する（`ReceiveStock`）— 起点: 外部トリガ（調達 / Procurement）

```java
record ReceiveStock(ReceiptId receiptId, Sku sku, Quantity quantity)
record StockReceived(ReceiptId receiptId, Sku sku, Quantity quantity)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 数量がゼロ | `InvalidQuantityException` |

- **集約の誕生**: コンストラクタコマンド（在庫の `@CreationPolicy(CREATE_IF_MISSING)` とは異なる）。
  入荷は `ReceiptId` を**外から与えられて1回だけ生まれる**ため、同一IDの二重受入はイベントストアの
  一意性制約で拒否される（Axon が集約ストリーム作成時に例外）。在庫が「その棚マスに初めて物が入った瞬間に
  生まれる」のと対照的で、**識別子の出所が違えば誕生の作り方も違う**。
- 状態遷移: `receivedQty = quantity`／`putAwayQty = ZERO`／`closed = false`

#### 2. 格納する（`PutAwayStock`）— 起点: 倉庫作業者

```java
record PutAwayStock(ReceiptId receiptId, LocationId locationId, Quantity quantity)
record StockPutAway(ReceiptId receiptId, Sku sku, LocationId locationId, Quantity quantity)
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| 数量がゼロ | `InvalidQuantityException` |
| クローズ済み | `ReceiptAlreadyClosedException` |
| **格納量 > 残格納量** | **`PutAwayExceedsRemainingException`** ← 不変条件 |

- **分割格納を許す**（1入荷を複数ロケーションへ分けて置くのは現実に起きる）。ロケーションは**格納時に確定**する。
- イベントに `sku` を載せるのは、**ポリシー P1 が入荷集約を引かずに `PlaceStock` を組み立てられる**ようにするため
  （在庫の識別子は `(Sku, LocationId)`）。プロジェクションが集約の状態を引かないのと同じ理由。
- **残格納量がゼロになったら、続けて `InboundReceiptClosed(残量0, COMPLETED)` を原子的に発行する**（H14）。
- 状態遷移: `putAwayQty += quantity`（残ゼロならクローズ）

#### 3. 入荷をクローズする（`CloseInboundReceipt`）— 起点: 倉庫作業者（破損・欠品）

```java
record CloseInboundReceipt(ReceiptId receiptId, ClosureReason reason)
record InboundReceiptClosed(ReceiptId receiptId, Quantity remainingQuantity, ClosureReason reason)
enum ClosureReason { COMPLETED, DAMAGED, SHORTAGE }   // 全量格納 / 破損 / 欠品
```

| 受付ゲート（拒否条件） | 例外 |
|---|---|
| クローズ済み（二重打ち切り） | `ReceiptAlreadyClosedException` |
| 理由が `COMPLETED`（全量格納は自動発行のみ） | `InvalidClosureReasonException` |

- **残格納量が残っていても通す**。破損・欠品という現実を**残量と理由として記録して**終わらせる
  （隠さない＝在庫側 H10 / H12 と同じ姿勢）。**判断の理由・却下した選択肢は
  [`decisions.md`](decisions.md#h14-格納しきれなかった入荷の終わらせ方) を参照。**
- **冪等にしない**（凍結・解凍とは逆）。発行元が人間のアクターであり、二重打ち切りは誤操作として伝えるべきため。
- 状態遷移: `closed = true`／`closureReason = reason`

### 例外一覧

| 例外 | 意味 |
|---|---|
| `PutAwayExceedsRemainingException` | 格納量 > 残格納量。**入荷の不変条件違反** |
| `ReceiptAlreadyClosedException` | クローズ済みの入荷への格納・再クローズ |
| `InvalidClosureReasonException` | `COMPLETED` を指定した打ち切り要求（全量格納は自動発行のみ） |
| `InvalidQuantityException` | 数量ゼロ（在庫スライスと共有） |

### 在庫集約との接続（ポリシー P1）

`StockPutAway` → **P1（格納伝播）** → `PlaceStock(InventoryItemId(sku, locationId), quantity, receiptId)`。
1トランザクション1集約のため**結果整合**（H6）。

> **未決（⑤ ポリシースライスで決める）**: 格納先の在庫が**凍結中**だと `PlaceStock` は拒否される
> （`InventoryFrozenException`）。このとき入荷側は格納済みなのに在庫に反映されない**片落ち**が残る。
> 扱い方（リトライ／デッドレター／そもそも凍結中の棚には置かせない運用）は P1 の設計で決着させる。
> 関連: [`decisions.md`](decisions.md#h11-棚卸中の引当をどこまで許すか) H11「既知のリスク」。

### テスト骨子（Axon `AggregateTestFixture` / Given-When-Then）

**正常系**
1. 入荷すると入荷が生まれ、残格納量が受入量と等しくなる
2. 一部を格納すると残格納量が減る
3. 複数のロケーションへ分割して格納できる
4. 全量を格納すると、格納イベントに続けて**完了としてクローズされる**（`COMPLETED`・残量ゼロ）

**不変条件（異常系・`expectException` でイベントを発行しないこと）**
5. 残格納量を超える格納は拒否される ★不変条件
6. 受入量ゼロの入荷は拒否される
7. 数量ゼロの格納は拒否される
8. クローズ済みの入荷への格納は拒否される

**クローズ（H14）**
9. 残格納量を残したまま破損で打ち切ると、**残量と理由を持つ**クローズイベントが出る
10. 欠品で打ち切っても同様に残量が記録される
11. クローズ済みの入荷の二重打ち切りは拒否される（冪等にしない）
12. `COMPLETED` を指定した打ち切り要求は拒否される

---

## 以降のスライス（未着手）

- ③ 出荷（`Shipment`）… 状態遷移 出荷指示 → ピッキング → 出荷。引当明細（`AllocationId`）を持つ形になるはず（在庫側と接続する）。
- ④ 棚卸（`Stocktake`）… 差異を持たない。対象ロケーションの列挙とカウント。
- ⑤ ポリシー P1〜P4（`@EventHandler` → `CommandGateway`）／サーガ P5／リードモデル4種のスキーマ。
