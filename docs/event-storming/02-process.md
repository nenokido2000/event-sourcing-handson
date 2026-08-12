# イベントストーミング ②Process Modeling（プロセス）

> 前段: [`01-big-picture.md`](01-big-picture.md)。この段の狙い: 各ドメインイベントに
> **コマンド（命令形）・アクター・ポリシー・リードモデル**を紐付け、コマンド→イベントの流れと
> **集約をまたぐ結果整合（ポリシー/Saga）**を明らかにする。集約の最終確定・BC境界・ユビキタス言語は ③で行う。
>
> ⚠ **この文書は M1 時点の分析記録**。後から決まったことで表・図は書き換えず、注記だけを足している
> （何がいつ見えたかという順序自体が学習素材のため）。**現在のポリシー・リードモデルの一覧は
> [`../ubiquitous-language.md`](../ubiquitous-language.md) が正**（例: P6 引当解放は M2 で追加されたので下の表と図にはいない）。

## 決定を受けて立ち上がった集約（4つ）
H1〜H6 の決定から、整合性境界（不変条件を守る最小単位）として次の4集約が浮かぶ。

| 集約 | BC | 識別子 | 主な状態 | 不変条件 |
|---|---|---|---|---|
| 入荷（`InboundReceipt`） | 入荷（Receiving） | `ReceiptId` | SKU・受入量・残格納量 | 格納総量 ≤ 受入量（残量を負にしない） |
| **在庫（`InventoryItem`）★コア** | **在庫（Inventory）** | **`(Sku, LocationId)`** | 手持在庫・引当済・凍結中 | **引当可能 = 手持在庫 − 引当済 ≥ 0**／凍結中は物理を動かすコマンドを拒否 |
| 出荷（`Shipment`） | 出荷（Fulfillment） | `ShipmentId` | 明細（SKU・ロケーション・数量）・状態 | 出荷は引当済み分のみ・出荷指示→ピッキング→出荷 の順 |
| 棚卸（`Stocktake`） | 棚卸（Stocktaking） | `StocktakeId` | 対象ロケーション・カウント（実地値） | カウントは対象ロケーションに対してのみ（**差異は持たない**） |

> BC と集約が同名になるのは正常（例: 注文コンテキストの中心にある集約は注文集約）。**BCの名前はその領域の中心概念から取られる**ため。
> 区別は名前ではなく**種別語**が担う（「入荷コンテキスト」「入荷集約」）。詳細は [`03-software-design.md`](03-software-design.md) の「集約の判定基準」。

## コマンド → イベント（アクター・ポリシー・リードモデル）

### 入荷コンテキスト（Receiving）— 入荷集約（`InboundReceipt`）
| コマンド（命令形） | アクター | 生成イベント | 参照リードモデル | 備考 |
|---|---|---|---|---|
| 入荷する（`ReceiveStock`） | 入荷担当 | 在庫が入荷された（`StockReceived`） | 入荷予定ビュー（発注情報・任意） | 外部トリガ「発注が確定した」を起点。ロケーション未確定 |
| 検品する（`InspectStock`）❓H8 | 検品担当 | 在庫が検品された（`StockInspected`） | — | 独立化するかは保留。合否を記録 |
| 格納する（`PutAwayStock`） | 格納担当 | 在庫が格納された（`StockPutAway`） | ロケーション空き容量ビュー | 格納先ロケーションを選定。入荷の残格納量を引き落とす |

### 在庫コンテキスト（Inventory）★コア — 在庫集約（`InventoryItem`）
| コマンド | アクター/起点 | 生成イベント | 参照リードモデル | 備考 |
|---|---|---|---|---|
| 在庫を計上する（`PlaceStock`） | ポリシーP1（格納伝播） | 在庫が計上された（`StockPlaced`） | — | 手持在庫↑・引当可能化。格納の在庫（Inventory）側の帰結 |
| 引き当てる（`AllocateStock`） | ポリシーP2（引当） | 在庫が引き当てられた（`StockAllocated`） | 引当可能在庫ビュー（`AvailableStockView`） | 引当可能 ≥ 要求量 の時のみ。違反は例外（イベント発行せず）。引当先選定では**棚卸中ロケーションを後回し**（→H11） |
| 引当を解除する（`DeallocateStock`） | 引当取消/期限切れ | 引当が解除された（`StockDeallocated`） | 引当ビュー（`AllocationView`） | 取消・タイムアウト等 |
| 在庫を払い出す（`IssueStock`） | ポリシーP3（出庫反映） | 在庫が払い出された（`StockIssued`） | — | ピッキングの帰結。手持在庫↓・引当済↓（同額ずつ減るので引当可能は不変）。**H7確定** |
| 在庫を調整する（`AdjustStock`） | ポリシーP4（棚卸反映） | 在庫が調整された（`StockAdjusted`） | — | **実地値を渡し、差分は集約が出す**（帳簿値を強整合で知るのは集約だけ）。増減両方。**H10確定** |
| 凍結する（`FreezeStock`） | サーガP5（棚卸凍結） | 在庫が凍結された（`StockFrozen`） | — | 棚卸対象になった。以降 物理を動かすコマンドを拒否 |
| 解凍する（`UnfreezeStock`） | サーガP5（棚卸凍結） | 在庫が解凍された（`StockUnfrozen`） | — | 棚卸クローズで通常運用へ戻す |

> **凍結中の受付可否**: 物理が動く **`PlaceStock`（格納）/ `IssueStock`（払出）は拒否**、
> 物理が動かない **`AllocateStock`（引当）は通す**、`AdjustStock` は棚卸のためのコマンドなので通す。
> 判断の理由は [`../decisions.md`](../decisions.md#h11-棚卸中の引当をどこまで許すか)（H11）。

### 出荷コンテキスト（Fulfillment）— 出荷集約（`Shipment`）
| コマンド | アクター/起点 | 生成イベント | 参照リードモデル | 備考 |
|---|---|---|---|---|
| 出荷を指示する（`RequestShipment`） | 外部トリガ | 出荷が指示された（`ShipmentRequested`） | 引当ビュー（`AllocationView`） | 上流からの薄い外部トリガ・**注文単位**（**H9確定**） |
| ピッキングする（`PickStock`） | ピッキング担当 | 在庫がピッキングされた（`StockPicked`） | ピックリストビュー | 物理的に棚から取る。在庫側の残高はポリシー P3 が減らす（**H7確定**） |
| 出荷する（`ShipStock`） | 出荷担当 | 在庫が出荷された（`StockShipped`） | — | 出荷完了の事実のみ。在庫の残高はピッキングで確定済み（**H7確定**） |

### 棚卸コンテキスト（Stocktaking）— 棚卸集約（`Stocktake`）
**責任分界（棚卸スライスで確定）**: 棚卸は「棚Aには42個あった」という**実情の把握とレポート**に徹する。
**差異を持たない・知らない**。帳簿値の権威者は在庫集約（自分のイベント列から導出＝強整合）なので、
実地値を渡せば**差分は在庫集約が自分で出せる**。差異（帳簿値−実地値）は集約をまたぐ**導出値**であり、
誰の不変条件も守らないため、リードモデル（**棚卸差異ビュー**）に置く。
＝ 引当可能（1集約内の導出値・不変条件を守るので集約が持つ）と対になる線引き。

| コマンド | アクター | 生成イベント | 参照リードモデル | 備考 |
|---|---|---|---|---|
| 棚卸を開始する（`StartStocktake`） | 棚卸責任者 | 棚卸が開始された（`StocktakeStarted`） | 引当可能在庫ビュー（対象ロケーションの在庫を確認） | **循環棚卸＝ロケーション単位**で対象範囲を確定 |
| カウントする（`CountStock`） | 棚卸担当 | 実地数量がカウントされた（`StockCounted`） | 棚卸差異ビュー（`StocktakeVarianceView`） | 実地数量を記録。差異が大きければ**同じコマンドで数え直す** |
| 棚卸をクローズする（`CloseStocktake`） | 棚卸責任者 | 棚卸がクローズされた（`StocktakeClosed`） | — | 全対象を数え終えたら終了。→ P5 が解凍する |

> **対象範囲はロケーション1軸**（SKU軸・カテゴリ軸を別に持たない）。
> 理由は [`../decisions.md`](../decisions.md#h10-棚卸調整の表現)（H10 の帰結）。

## ポリシー（集約をまたぐ結果整合 = Saga / Process Manager）
「1トランザクション1集約」を守るため、集約をまたぐ整合はイベント→ポリシー→コマンドで結果整合にする。

| ポリシー | 反応するイベント | 発行するコマンド | またぐ集約 | 役割 |
|---|---|---|---|---|
| **P1 格納伝播（`PutawayPolicy`）** | 在庫が格納された（`StockPutAway`／入荷） | 在庫を計上する（`PlaceStock`／在庫） | 入荷 → 在庫 | 入荷集約から在庫集約へ在庫を移す。手持在庫 ↑ |
| **P2 引当（`AllocationPolicy`）★コア** | 「受注が受け付けられた」（外部） | 引き当てる（`AllocateStock`／在庫） | 受注（Ordering・外部） → 在庫 | 引当可能在庫ビューを見て引当先ロケーションを選定・引当。**棚卸中ロケーションは後回し**（→H11） |
| **P3 出庫反映（`FulfillmentPolicy`）** | 在庫がピッキングされた（`StockPicked`／出荷） | 在庫を払い出す（`IssueStock`／在庫） | 出荷 → 在庫 | ピッキングを在庫へ反映。手持在庫 ↓。※`PickStock`/`ShipStock` 自体は👤アクター駆動でポリシーではない |
| **P4 棚卸反映（`StocktakePolicy`）** | 実地数量がカウントされた（`StockCounted`／棚卸） | 在庫を調整する（`AdjustStock(実地値)`／在庫） | 棚卸 → 在庫 | 実地値をそのまま在庫へ渡す（差分は在庫が出す）。手持在庫 ↕ |
| **P5 棚卸凍結（`StocktakeFreezeSaga`）★唯一のSaga** | 棚卸が開始された／クローズされた（`StocktakeStarted`／`StocktakeClosed`） | 凍結する／解凍する（`FreezeStock`／`UnfreezeStock`／在庫） | 棚卸 → 在庫（**複数**） | 対象ロケーションの在庫集約を列挙し1件ずつ凍結／クローズで解凍。**状態を持つ**（何件凍結済みか・未解凍はどれか） |

> **この表は①②の段での結論**。M2 戦術設計の出荷スライスで **P6 引当解放（`AllocationReleasePolicy`）が追加**され、
> ポリシーは6本になった（欠品出荷・出荷取消で宙に浮いた引当を解除する）。
> → [`../decisions.md`](../decisions.md#h17-宙に浮いた引当を誰が解放するか) H17

### 発見: 4本は単発の伝播、1本だけが Process Manager
P1・P3・P4 は**同型**である——「他のBCで起きた**物理的な事実**を、在庫の残高へ反映する」だけ。

```
P1 格納伝播 : 🟧在庫が格納された（入荷）      → 🟦在庫を計上する   → 手持在庫 ↑
P3 出庫反映 : 🟧在庫がピッキングされた（出荷） → 🟦在庫を払い出す   → 手持在庫 ↓
P4 棚卸反映 : 🟧実地数量がカウントされた（棚卸）→ 🟦在庫を調整する   → 手持在庫 ↕
```

差異検出という*計算*を担っていた旧 P4 だけが異物だったが、実情の伝達に揃った。ここから
**在庫集約が受ける入力はすべて外から来た物理的事実**だと読める（自分からは動かない。棚の残高は
入口＝格納・出口＝ピッキング・現実との突き合わせ＝棚卸 でしか変わらない）。

> **M2 での補足**: 後から加わった P6（引当解放）は**残明細の数だけコマンドを送る 1:N** なので、
> この「単発の伝播」の形からは外れる。ただし**状態を持たない**ので Saga ではない
> ——**Policy と Saga を分ける基準は fan-out の有無ではなく途中状態の有無**だと、この例外がむしろ明確にした（H17）。

対して **P5 だけが本物の Saga（Process Manager）**。対象ロケーションの在庫集約は複数あり
（棚Aに10SKUなら10集約）、1トランザクション1集約なので1件ずつ凍結するしかない。
→ ①対象集約の**列挙**が要る（棚卸集約は他集約を知らないのでリードモデルを見る）
②全部凍結し終わるまでの**途中状態**が存在する ③数え終わったら**全部解凍**する必要があり、
未解凍がどれかを覚えている主体が要る。**開始と終了があり途中状態を持つ**＝ Saga の定義そのもの。

**含意（M2/M3の設計判断が1つ片付く）**: P1〜P4 は状態を持たないので素直なイベントハンドラで十分、
**Axon の Saga を使うのは P5 だけ**。使う理由が「学習のため」ではなく**ドメインが要求したから**になる。
語尾もこれに合わせた（状態なし＝`...Policy` / 状態あり＝`...Saga`）。

> ⚠ **凍結対象の列挙は結果整合**（リードモデル経由）。直前に格納された在庫が投影に載っておらず
> 凍結漏れする可能性がある。PoCでは許容し、ホットスポットとして記録する（実務では格納側も
> 同じロケーション単位でロックする等の対策が要る）。リスク窓は循環棚卸の粒度を小さくするほど縮む。

## プロセス全体図（👤アクター/↩外部 →（📄リードモデル参照）→ 🟦コマンド →〔集約〕→ 🟧イベント →💜ポリシー）

**文法を完全化**した図。コマンドは必ず **👤アクター（📄リードモデルを見て判断）/ 💜ポリシー（自動反応）/ ↩外部システム** のいずれかが発する
＝**イベントが直接コマンドを生む線は無い**。集約は点線枠＝整合性境界（コマンドが枠に入り枠内でイベントが出る）。
枠をまたぐ自動連鎖は💜ポリシー経由（1Tx1集約）。リードモデルはイベントから投影（CQRS、点線「投影」）。

> 📌 **この図は M1 時点の記録**。M2 の戦術設計で **P6（引当解放）・出荷取消・入荷クローズ・
> `StartStocktake` の前段検証**が加わり、**リードモデルの顔ぶれも変わった**（ここに描いた入荷予定／
> ロケーション空き容量／ピックリストの各ビューは M2 の5種に残っていない）。
> **現在形は [`tactical-design.md` のプロセス全体図](../tactical-design.md#プロセス全体図m2-現在形)** を参照。
> この図は分析の記録として残す（書き換えない）。

> **配色は[イベントストーミング標準記法](00-method.md#付箋の色イベントストーミング標準記法この節が正)**
> （🟧オレンジ=イベント / 🟦青=コマンド / 💜ライラック=ポリシー・サーガ / 📄緑=リードモデル / ↩ピンク=外部システム /
> 👤黄=アクター / 淡黄の枠=集約）。**この図の時間は上→下**（壁の時系列そのものではなく*文法*の可視化。
> 左→右の壁レイアウトは [`01-big-picture.md`](01-big-picture.md) が担う）。

```mermaid
flowchart TB
    %% 付箋の色 = イベントストーミング標準記法（正は 00-method.md「付箋の色」）
    classDef evt   fill:#ffb366,stroke:#e07b1a,color:#3d2000;
    classDef cmd   fill:#7fbfe8,stroke:#2b7cb8,color:#062033;
    classDef actor fill:#ffe066,stroke:#c9a227,color:#3d3300;
    classDef pol   fill:#d9c2f0,stroke:#8b5cc4,color:#2e1650;
    classDef rm    fill:#a8e6a3,stroke:#3f9e3a,color:#0f2f0d;
    classDef ext   fill:#ffb3d1,stroke:#d1568f,color:#4a0f2b;

    %% ── 外部システム（↩）──
    X_PO["↩ 発注が確定した"]:::ext
    X_ORD["↩ 受注が受け付けられた"]:::ext
    X_SHREQ["↩ 出荷が指示された<br/>(ShipmentRequested)"]:::ext
    X_CANCEL["↩ 注文が取り消された / 期限切れ"]:::ext

    %% ── アクター（👤）──
    A_RCV["👤 入荷担当"]:::actor
    A_PUT["👤 格納担当"]:::actor
    A_PICK["👤 ピッキング担当"]:::actor
    A_SHIP["👤 出荷担当"]:::actor
    A_STK["👤 棚卸責任者"]:::actor
    A_CNT["👤 棚卸担当"]:::actor

    %% ── リードモデル（📄・イベントから投影）──
    RM_INBOUND["📄 入荷予定ビュー"]:::rm
    RM_LOC["📄 ロケーション空き容量ビュー"]:::rm
    RM_AVAIL["📄 引当可能在庫ビュー<br/>AvailableStockView"]:::rm
    RM_PICK["📄 ピックリストビュー"]:::rm
    RM_VAR["📄 棚卸差異ビュー<br/>StocktakeVarianceView"]:::rm
    RM_LEDGER["📄 在庫元帳ビュー<br/>StockLedgerView"]:::rm

    %% ── コマンド（🟦）──
    C_RCV["入荷する<br/>(ReceiveStock)"]:::cmd
    C_PUT["格納する<br/>(PutAwayStock)"]:::cmd
    C_PLACE["在庫を計上する<br/>(PlaceStock)"]:::cmd
    C_ALLOC["引き当てる<br/>(AllocateStock)"]:::cmd
    C_DEALLOC["引当を解除する<br/>(DeallocateStock)"]:::cmd
    C_PICK["ピッキングする<br/>(PickStock)"]:::cmd
    C_ISSUE["在庫を払い出す<br/>(IssueStock)"]:::cmd
    C_SHIP["出荷する<br/>(ShipStock)"]:::cmd
    C_STK["棚卸を開始する<br/>(StartStocktake)"]:::cmd
    C_CNT["カウントする<br/>(CountStock)"]:::cmd
    C_CLOSE["棚卸をクローズする<br/>(CloseStocktake)"]:::cmd
    C_ADJ["在庫を調整する<br/>(AdjustStock(実地値))"]:::cmd
    C_FREEZE["凍結する<br/>(FreezeStock)"]:::cmd
    C_UNFREEZE["解凍する<br/>(UnfreezeStock)"]:::cmd

    %% ── ポリシー（💜・自動反応）──
    P1["💜 P1 格納伝播"]:::pol
    P2["💜 P2 引当ポリシー ★コア"]:::pol
    P3["💜 P3 出庫反映"]:::pol
    P4["💜 P4 棚卸反映"]:::pol
    P5["💜 P5 棚卸凍結<br/>Saga(状態あり)"]:::pol

    %% ── 集約（整合性境界）＝サブグラフ ──
    subgraph AG_RCV["🧺 入荷 InboundReceipt"]
      E_RCV["在庫が入荷された<br/>(StockReceived)"]:::evt
      E_PUT["在庫が格納された<br/>(StockPutAway)"]:::evt
    end
    subgraph AG_INV["📦 在庫 InventoryItem ★コア"]
      E_PLACE["在庫が計上された<br/>(StockPlaced)<br/>手持在庫↑・引当可能化"]:::evt
      E_ALLOC["在庫が引き当てられた<br/>(StockAllocated)"]:::evt
      E_DEALLOC["引当が解除された<br/>(StockDeallocated)"]:::evt
      E_ISSUE["在庫が払い出された<br/>(StockIssued)<br/>手持在庫↓・引当済↓"]:::evt
      E_ADJ["在庫が調整された<br/>(StockAdjusted)<br/>差分±・実地値・原因StocktakeId"]:::evt
      E_FROZEN["在庫が凍結された<br/>(StockFrozen)"]:::evt
      E_UNFROZEN["在庫が解凍された<br/>(StockUnfrozen)"]:::evt
    end
    subgraph AG_SHIP["🚚 出荷 Shipment"]
      E_PICK["在庫がピッキングされた<br/>(StockPicked)"]:::evt
      E_SHIP["在庫が出荷された<br/>(StockShipped)"]:::evt
    end
    subgraph AG_STK["📋 棚卸 Stocktake（差異は持たない）"]
      E_STKSTART["棚卸が開始された<br/>(StocktakeStarted)"]:::evt
      E_CNT["実地数量がカウントされた<br/>(StockCounted)"]:::evt
      E_STKCLOSE["棚卸がクローズされた<br/>(StocktakeClosed)"]:::evt
    end

    %% ── 入荷〜格納（人の判断）──
    X_PO --> A_RCV
    RM_INBOUND -.参照.-> A_RCV
    A_RCV --> C_RCV --> E_RCV
    E_RCV --> A_PUT
    RM_LOC -.参照.-> A_PUT
    A_PUT --> C_PUT --> E_PUT
    E_PUT --> P1 --> C_PLACE --> E_PLACE

    %% ── 引当（自動・コア）──
    X_ORD --> P2
    RM_AVAIL -.参照.-> P2
    P2 --> C_ALLOC --> E_ALLOC
    X_CANCEL --> C_DEALLOC --> E_DEALLOC

    %% ── 出荷（人の判断）──
    X_SHREQ --> A_PICK
    RM_PICK -.参照.-> A_PICK
    A_PICK --> C_PICK --> E_PICK
    E_PICK --> A_SHIP --> C_SHIP --> E_SHIP
    E_PICK --> P3 --> C_ISSUE --> E_ISSUE

    %% ── 棚卸（人＋自動）──
    RM_AVAIL -.対象ロケーションを参照.-> A_STK
    A_STK --> C_STK --> E_STKSTART
    E_STKSTART --> P5 --> C_FREEZE --> E_FROZEN
    E_STKSTART --> A_CNT
    RM_VAR -.差異を見て数え直しを判断.-> A_CNT
    A_CNT --> C_CNT --> E_CNT
    E_CNT --> P4 --> C_ADJ --> E_ADJ
    E_CNT -.全対象を数え終えたら.-> A_STK
    A_STK --> C_CLOSE --> E_STKCLOSE
    E_STKCLOSE --> P5 --> C_UNFREEZE --> E_UNFROZEN

    %% ── CQRS: リードモデルはイベントから投影 ──
    E_PLACE -.投影.-> RM_AVAIL
    E_ALLOC -.投影.-> RM_AVAIL
    E_ADJ -.投影.-> RM_AVAIL
    E_FROZEN -.棚卸中フラグを投影.-> RM_AVAIL
    E_UNFROZEN -.投影.-> RM_AVAIL
    E_ALLOC -.投影.-> RM_PICK
    E_CNT -.投影.-> RM_VAR
    E_ADJ -.投影.-> RM_VAR
    E_PLACE -.投影.-> RM_LEDGER
    E_ISSUE -.投影.-> RM_LEDGER
    E_ADJ -.投影.-> RM_LEDGER

    %% ── 集約の枠 ＝「大きい淡黄の付箋」（整合性境界）──
    style AG_RCV fill:#fff9d6,stroke:#c9ad2e,stroke-width:2px,stroke-dasharray:5 4,color:#3d3300
    style AG_INV fill:#fff2a8,stroke:#c9ad2e,stroke-width:3px,stroke-dasharray:5 4,color:#3d3300
    style AG_SHIP fill:#fff9d6,stroke:#c9ad2e,stroke-width:2px,stroke-dasharray:5 4,color:#3d3300
    style AG_STK fill:#fff9d6,stroke:#c9ad2e,stroke-width:2px,stroke-dasharray:5 4,color:#3d3300
```

> 読み方: **コマンド（青）の手前には必ず 👤/💜/↩ がある**＝「誰・何が決めたか」が見える。枠（集約）をまたぐ自動連鎖は💜ポリシー経由（1Tx1集約）。
> ※ 棚卸スライスで **`E_CNT --> E_DISC`（イベント→イベント直結）という唯一の文法違反を解消**した（間にコマンドが不在だった）。
> 現在は `実地数量がカウントされた → 💜P4 → 在庫を調整する → 在庫が調整された` と文法どおりに繋がっている。
> 📄リードモデルは点線「投影」でイベントから作られ（CQRS）、👤や💜が判断時に参照する。★=コアの引当。

### 発見: 各コマンドは「自動（ポリシー）」か「人の判断（アクター＋リードモデル）」か
文法を完全化すると、**何が自動化され何が人の判断か**が一望できる（これ自体が設計上の発見）。

| コマンド | 発生源 | 種別 | 参照リードモデル |
|---|---|---|---|
| 入荷する / 格納する | 👤入荷担当 / 👤格納担当 | 人の判断 | 入荷予定 / ロケーション空き容量 |
| 在庫を計上する | 💜P1 格納伝播 | 自動 | — |
| **引き当てる ★コア** | 💜P2 引当ポリシー | 自動 | 引当可能在庫ビュー |
| 引当を解除する | ↩注文取消 / 期限切れ | 外部・タイマー | — |
| ピッキングする / 出荷する | 👤ピッキング担当 / 👤出荷担当 | 人の判断 | ピックリスト |
| 在庫を払い出す | 💜P3 出庫反映 | 自動 | — |
| 棚卸を開始する / カウントする / クローズする | 👤棚卸責任者 / 👤棚卸担当 | 人の判断 | 引当可能在庫（対象確定）/ 棚卸差異（数え直し判断） |
| 在庫を調整する | 💜P4 棚卸反映 | 自動 | — |
| 凍結する / 解凍する | 💜P5 棚卸凍結（Saga） | 自動 | 引当可能在庫（対象集約の列挙） |

**含意**: コア（引当）は**自動化**され、入出庫の物理作業は**人**が担う。将来「引当先を人が選ぶ」「格納を自動化」等の変更は、この*発生源の差し替え*として現れる（M3+改修シナリオの着眼点）。

## この段で解けた / 残した論点

文法を全イベントに当てた結果、①から持ち越した論点のうち4つが解け、1つが新たに立った。
**決定の理由・却下した選択肢は [`../decisions.md`](../decisions.md)（ADR）が正**——ここには結果だけを置く。

| # | 論点 | 結果 |
|---|---|---|
| [H6](../decisions.md#h6-受入在庫の集約帰属) | 受入在庫の集約帰属 | **解決**: 受入は `InboundReceipt` 集約、格納で `InventoryItem` へ移す（P1）→ ③で BC 境界に反映 |
| [H7](../decisions.md#h7-在庫量が減るタイミング) | 在庫量が減るタイミング | **解決**: ピッキング時に 手持在庫↓・引当済↓（P3 → `IssueStock`）。`StockShipped` は残高を動かさない |
| [H9](../decisions.md#h9-出荷指示の出所と粒度) | 出荷指示の出所と粒度 | **解決**: 薄い外部トリガのまま・注文単位。バッチ化は M3+ 候補へ |
| [H10](../decisions.md#h10-棚卸調整の表現) | 棚卸調整の表現 | **解決**: 実地値を渡し差分は在庫集約が出す。差異はリードモデルへ |
| [H11](../decisions.md#h11-棚卸中の引当をどこまで許すか) | 棚卸中の引当 | **新規・暫定**: 通す＋P2で引当先を後回し。実績が出たら見直す → M3+ 候補② |
| [H8](../decisions.md#h8-検品を独立させるか) / [H5](../decisions.md#h5-ロケーション間の在庫移動) | 検品の独立化 / 在庫移動 | **保留**（H8 は M2 で内包に決着、H5 は M3+ 候補） |

> **この段の収穫**: H10 は「イベント→イベントの直線」という**文法違反**を埋めようとして解けた。
> 図を文法どおりに描き切ること自体が分析の道具になる、という例
> （経緯は [`00-method.md`](00-method.md) の導出の物語③・④）。

## 次工程（③Software Design）への申し送り
- 4集約を核に **BC境界の確定 → コンテキストマップ**（受注（Ordering）は上流の外部トリガ＝Customer/Supplier 関係）。
- **コアサブドメイン = 在庫引当（P2 AllocationPolicy ＋ InventoryItem の不変条件）**を明示し、支援（Receiving/Fulfillment/Stocktaking）・汎用と区別。
- **ユビキタス言語表**（用語・意味・英名）を確定。
