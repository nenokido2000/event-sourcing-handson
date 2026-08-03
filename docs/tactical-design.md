# 戦術設計（M2） — 倉庫在庫管理

> 前段: [`event-storming/`](event-storming/)（M1 戦略設計）。この文書の狙いは、分析で確定した集約・コマンド・
> イベント・不変条件を **型レベル（フィールド・シグネチャ・受付ゲート・例外）まで落とす**こと。実装（M3）は
> この文書を仕様として TDD で駆動する。用語の正は [`ubiquitous-language.md`](ubiquitous-language.md)。
>
> **進め方**: 集約ごとのスライスで確定していく。コア（在庫）→ 入荷 → 出荷 → 棚卸。
>
> | スライス | 状態 |
> |---|---|
> | ① 在庫（`InventoryItem`）★コア | **確定**（2026-08-03） |
> | ② 入荷（`InboundReceipt`） | 未着手 |
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
> 棚卸開始**前**からある引当は凍結（P5）では止められないため、実地値が引当済を下回ると引当可能は負に落ちる
> （例: 手持在庫50・引当済45 の棚を数えたら42 → 引当可能 −3）。**現実を帳簿に書けないほうが有害**なので調整は必ず通し、
> 負に落ちた瞬間に新規引当が全部止まる（`引当可能 ≥ 要求量` を満たせない）ことで被害の拡大を集約が止める。
> 余剰引当の解消は自動化せず運用に寄せる。詳細は [`event-storming/03-software-design.md`](event-storming/03-software-design.md) の H12。

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

- **数量については拒否条件を持たない**のがこのコマンドの特徴。凍結中でも通し、引当可能が負になっても通す（H12）。
  在庫集約にとって実地値は**外から来た物理的な事実**であり、拒否する立場にない。
- ただし**呼び出し元の正当性は別の関心事**なので、他の棚卸が凍結中の棚への調整は拒否する（同じ棚を2つの棚卸が
  同時に触っている＝明確な矛盾）。解凍（`UnfreezeStock`）と同じ照合を掛ける。
- **凍結されていない場合は通す**（拒否しない）。凍結対象の列挙は結果整合で**凍結漏れが起こりうる**ことは
  既に許容済みの事項（[`event-storming/02-process.md`](event-storming/02-process.md)）であり、
  凍結漏れを理由に**実際に数えた事実を捨てるほうが有害**なため。H12 と同じ「現実を記録する」判断。
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

## 以降のスライス（未着手）

- ② 入荷（`InboundReceipt`）… H8 確定により検品は内包。不変条件「格納累計 ≤ 受入量」。
- ③ 出荷（`Shipment`）… 状態遷移 出荷指示 → ピッキング → 出荷。引当明細（`AllocationId`）を持つ形になるはず（在庫側と接続する）。
- ④ 棚卸（`Stocktake`）… 差異を持たない。対象ロケーションの列挙とカウント。
- ⑤ ポリシー P1〜P4（`@EventHandler` → `CommandGateway`）／サーガ P5／リードモデル4種のスキーマ。
