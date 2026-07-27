# イベントストーミング ③Software Design（設計への橋渡し）

> 前段: [`01-big-picture.md`](01-big-picture.md) / [`02-process.md`](02-process.md)。
> この段の狙い: 集約候補の確定、**境界づけられたコンテキスト(BC)**とコンテキストマップ、
> **コアサブドメイン**の特定、**ユビキタス言語**の確定。関連成果物は
> [`../context-map.md`](../context-map.md) と [`../ubiquitous-language.md`](../ubiquitous-language.md)。

## 集約（確定）
不変条件（整合性境界）で括った結果、次の4集約に落ち着く。他集約は**識別子で参照**し、
またぐ整合は**ポリシー/Saga による結果整合**（[`02-process.md`](02-process.md) の P1〜P4）で扱う。

| 集約 | 識別子 | 状態（値オブジェクト） | 不変条件 | ライフサイクル |
|---|---|---|---|---|
| 入荷ロット（`InboundReceipt`） | `ReceiptId` | SKU（`Sku`）・受入量（`Quantity`）・残格納量（`Quantity`） | 格納累計 ≤ 受入量 | 受入 →(検品)→ 格納完了でクローズ |
| **在庫アイテム（`InventoryItem`）★コア** | `InventoryItemId = (Sku, LocationId)` | 手持在庫・引当済（各 `Quantity`） | **引当可能 = 手持在庫 − 引当済 ≥ 0** | 格納で誕生 → 引当/出荷/調整で状態遷移 |
| 出荷（`Shipment`） | `ShipmentId` | 明細（SKU・ロケーション・数量）・状態（`ShipmentStatus`） | 出荷は引当済み分のみ・状態は 出荷指示→ピッキング→出荷 | 出荷指示 → ピッキング → 出荷 |
| 棚卸（`Stocktake`） | `StocktakeId` | 対象ロケーション・カウント・差異 | カウントは対象ロケーションに限定 | 開始 → カウント → 差異記録 → クローズ |

### 値オブジェクト（原始型の乱用を避ける）
- `Sku`（商品識別）, `LocationId`（倉庫内ロケーション）, `Quantity`（非負整数。加減算と `≥` 比較を持つ）, `ReceiptId` / `ShipmentId` / `StocktakeId`。

## 境界づけられたコンテキスト（BC）とサブドメイン分類

| BC | 主な集約 | サブドメイン分類 | 理由 |
|---|---|---|---|
| **Inventory** | `InventoryItem`＋引当ポリシー＋リードモデル | **コア** | 事業の差別化＝**在庫引当（Stock Allocation）**。非自明な集約境界（SKU×ロケーション）と不変条件がここに集中 |
| Receiving | `InboundReceipt` | 支援 | 入荷〜格納。コアに在庫を供給するが差別化要素ではない |
| Fulfillment | `Shipment` | 支援 | 引当を消化して出荷。工程管理が主 |
| Stocktaking | `Stocktake` | 支援 | 実地棚卸で帳簿を補正。定期・横断 |
| Ordering / Procurement | （外部） | 汎用/外部 | 受注・発注は上流の薄い外部トリガとして扱う（本PoCでは内製しない） |

- **コアサブドメイン = 在庫引当（Stock Allocation）**。ここに設計・テストの投資を集中する（不変条件 引当可能 ≥ 0 の異常系、引当先ロケーション選定 P2）。
- 支援（Receiving/Fulfillment/Stocktaking）は素直に実装。汎用/外部（Ordering/Procurement）は外部トリガとして最小限。

## CQRS（読み書き分離）
- **書き込み側** = Axon 集約（イベントソース）。真実の源泉はイベントストアのみ。
- **リードモデル（プロジェクション）**（用途別に分ける・非正規化・冪等・再構築可能）:
  - `AvailableStockView`（引当可能在庫ビュー）… SKU×ロケーション別の引当可能数（引当の意思決定 P2 の入力）。
  - `AllocationView` … 引当の一覧（誰が/どの注文にいくつ引当済みか）。出荷・取消の入力。
  - `StockLedgerView` … 全在庫変動の履歴（受入・格納・引当・出荷・調整）。**イベントソーシングの旨味（履歴）**を可視化。
- クエリ側に不変条件・ビジネスルールを置かない（CQS 厳守）。

## パッケージ方針（コード落とし込みの指針・M2/M3で適用）
- BC 単位でパッケージを切る: `inventory` / `receiving` / `fulfillment` / `stocktaking`。BC をまたぐ直接依存を作らない（連携はイベント＋ポリシー）。
- 命名は [`.claude/rules/ddd-ubiquitous-language.md`](../../.claude/rules/ddd-ubiquitous-language.md)：コマンド=命令形、イベント=過去形、集約/値オブジェクト=名詞。
- モジュール配置: 集約/コマンド/イベント= `warehouse-domain`、コマンドハンドラ/Axon設定= `warehouse-command`、プロジェクション/リードモデル/クエリ= `warehouse-query`。

## M2（戦術設計）への申し送り（未決）
- **H7**: 在庫量の増減タイミング（引当/ピッキング/出荷で 手持在庫・引当済 がいつ動くか）の確定。
- **H8**: `StockInspected` を独立イベントにするか（既定寄り=内包で簡素）。
- **H9**: `ShipmentRequested` を外部トリガのままにするか Fulfillment 側ポリシーに格上げするか。
- **H5**: `StockMoved`（ロケーション間移動）を M3+ 改修シナリオとして具体化（P1 と同型の2集約またぎ）。
- **ATDD 受入シナリオ骨子**（言葉だけ）: 受入→格納→引当→ピッキング→出荷の正常系／過剰引当の拒否／過剰出荷の拒否／棚卸調整後の引当可能数の整合／在庫元帳に全履歴が並ぶこと。M3 で Gauge Spec 化。
