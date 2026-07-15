# イベントソーシング学習PoC 計画（倉庫 → ウォレット / Axon 4.x → 5.x → PHP）

> このドキュメントは実行計画。ES開発本体（M0〜M7）は段階的に進める。
> ステアリング機構（`CLAUDE.md` / `.claude/`）は先行して整備済み。

## Context（なぜこれをやるか）

**再現性のある学習・知見共有用のサンプル**を作る。「ドメイン分析（イベントストーミング）→ 戦略/戦術設計 →
実アプリへの落とし込み」までを、イベントソーシング + AWS(DynamoDB + DynamoDB Streams) 制約下で
一気通貫に体験・言語化することが目的。学習効率と認知負荷を最優先し、以下の方針で進める:

- **題材は2つ**: ①スマート倉庫在庫管理（複雑な集約境界で分析を鍛える／第1弾）、②ロイヤリティ・ポイントウォレット（ES/CQRS機構が綺麗／第2弾・復習＋インフラ深化）。
- **フレームワークは Axon Framework**。イベントストアは DynamoDB を独自実装で連携（Axonは公式非対応。具体的な実装方式は別途検討）。
- **Axonバージョン戦略（学習曲線を「既知→差分→応用」に）**:
  1. 倉庫を **Axon 4.x** で円滑に完成（手戻り最小）。
  2. 完成した倉庫を **4.x → 5.x へバージョンアップ**（焦点を差分確認に絞る。公式移行ガイド駆動）。
  3. その知見でウォレットを **最初から 5.x** で構築 → PHP比較のベースにする。
- **各題材内でも「ドメイン先行 → 自作DynamoDBエンジン後付け」**の順にし、分析・設計を最難関インフラに人質に取らせない。
- CQRSは、読み書き分離が妥当なので積極採用する。
- 完成後、他言語PoC（PHP + フレームワークTBD）へ展開する情報を整理する。

### 情報源（一次情報・逐次確認する）
- Axon リポジトリ（正）: <https://github.com/AxonIQ/AxonFramework>（旧 `AxonFramework/AxonFramework` はリダイレクト）
- リファレンス（版別）: <https://docs.axoniq.io/axon-framework-reference/>（4.x / 5.x 切替）
- 4→5 移行: <https://github.com/AxonIQ/AxonFramework/blob/main/axon-5/api-changes/index.md> ＋ 5.x migration ガイド
- 補足: Axon 5系は open-core 分割で「Axon Framework(OSS) / Axoniq Framework(商用寄り=Axon Server連携系)」に分離。**本PoCは Axon Server を使わずOSS側のみで完結**する。

## 環境前提（確認済み 2026-07）
- 作業ディレクトリ `/Users/naokienokido/event-sourcing`。Git未初期化（M0で `git init`）。
- Java 21 (Corretto) / Docker 29 + Compose v2 / Node 23 / Git あり。
- **未導入**: Maven, Gradle（→ Gradle Wrapper 同梱で解決）, PHP/Composer（→ Docker化）, AWS CLI（→ LocalStack + AWS SDK for Java v2。CLIが要る場面は `awslocal` コンテナ）。

## 技術スタック（第1弾・倉庫）
- 言語/ビルド: Java 21 / **Gradle (Kotlin DSL) + Wrapper**
- FW: **Axon Framework 4.x（最新4.12系）** + Spring Boot 3.x（axon-spring-boot-starter）
- 読みモデル(Query側): **PostgreSQL**（Docker）
- イベントストア: 段階導入（M3=組み込み → M4=DynamoDB自作）
- ローカルAWS: **LocalStack**（DynamoDB + DynamoDB Streams）。AWS SDK for Java v2。

## アーキテクチャ方針（倉庫）
- **境界づけられたコンテキスト**: Inventory(コア) / Receiving / Fulfillment / Stocktaking。上流に Ordering(薄い外部トリガ)。
- **コアサブドメイン**: 在庫引当（Stock Allocation）。
- **集約**: `InventoryItem`（SKU × 倉庫ロケーション単位）。不変条件 `available = onHand - allocated ≥ 0`。
  - コマンド: `ReceiveStock` / `AllocateStock` / `DeallocateStock` / `ShipStock` / `AdjustStock(棚卸)`
  - イベント: `StockReceived` / `StockAllocated` / `StockDeallocated` / `StockShipped` / `StockAdjusted`
  - ※ Cosmic Python の allocation 例に近い、意図的に非自明な集約境界。
- **CQRS（読み書き分離）**:
  - 書き込み側 = Axon 集約（イベントソース）。
  - 読み側プロジェクション: `AvailableStockView` / `AllocationView` / `StockLedgerView`（全在庫変動履歴＝履歴のうまみを可視化）。
- **プロジェクションの給餌方式（段階）**:
  - M3(組み込みストア期): Axon の TrackingEventProcessor で素直に投影。
  - M4(DynamoDB期): **DynamoDB Streams → ストリーム消費プロセス(LocalStack) → PostgreSQL投影** に切替（AWS本番パターンに一致。Axon の追跡機構はバイパス）。

## モジュール構成（Gradle マルチモジュール）
```
event-sourcing/
  docs/                          # plan.md ＋ 分析・設計の成果物（Markdown＋Mermaid）
  .claude/                       # ステアリング（rules/hooks/skills/agents）
  infra/                         # docker-compose(LocalStack, PostgreSQL), 初期化スクリプト
  warehouse-domain/              # 集約・コマンド・イベント（純ドメイン）
  warehouse-command/             # コマンドハンドラ・Axon設定
  warehouse-query/               # プロジェクション・読みモデル・クエリハンドラ
  warehouse-eventstore-dynamodb/ # M4で追加: AbstractEventStorageEngine のDynamoDB実装
  warehouse-app/                 # Spring Boot起動・REST API
  gradlew, gradle/wrapper/...    # Wrapper同梱
```

## マイルストーン（分析最優先・段階的・各段で成果物を残す）
- **M0 — 足場**: `git init`、Gradleマルチモジュール雛形、`infra/docker-compose.yml`(LocalStack+Postgres)、README。（→ ここでステアリングのゲートが有効化）
- **M1 — 倉庫の戦略設計（分析成果物）**: イベントストーミング（Big Picture→Process→Design）を `docs/` にMermaidで記録。BC/コンテキストマップ/コアサブドメイン特定。← **最重視**（`event-storming` スキル活用）
- **M2 — 倉庫の戦術設計（成果物）**: 集約境界・コマンド/イベント/ポリシー・不変条件・ユビキタス言語を `docs/` に整理。
- **M3 — 倉庫実装 (Axon 4.x / 組み込みストア)**: 受入→引当→出荷の**動く垂直スライス** + 3プロジェクション + REST API。
- **M4 — 自作DynamoDBイベントストア (4.x)**: `AbstractEventStorageEngine` をDynamoDBで実装。投影をDynamoDB Streams駆動へ切替。LocalStackで一気通貫。← AWS制約を満たす山場。
- **M5 — 倉庫を Axon 5.x へアップグレード**: 公式移行ガイド駆動の**差分作業**。(5-a) ドメイン/アプリを5.xへ（イベントストアは5.x組み込みへ一旦フォールバック）→ (5-b) 自作DynamoDBエンジンを5.x SPI(非同期/AppendCondition・DCB)へ移植。差分は `docs/axon4-to-5-migration.md` に記録。
- **M6 — ウォレットを最初から 5.x で構築**: 集約=会員ウォレット, 不変条件=残高≥0, 失効=時間駆動, 付与残高/会計負債でCQRS。復習＋インフラ理解の定着。
- **M7 — 他言語PoC(PHP)向け情報整理**: ウォレットを共通題材に、PHPフレームワーク選定基準（DDD/CQRS/ES適性・世界的普及度）を調査整理。候補メモ（例: Ecotone / Prooph / EventSauce）。**具体選定はこの段で判断**。

## 検証（各段のエンドツーエンド確認）
- M3: REST で `ReceiveStock`→`AllocateStock`→`ShipStock` → 3プロジェクション照会 → `available=onHand-allocated` が保たれ `StockLedgerView` に全履歴が並ぶ。過剰引当/出荷が不変条件で弾かれる。
- M4: `docker compose up` → 同シナリオ → LocalStackのDynamoDBに `aggregateIdentifier/sequenceNumber` 行が追記され条件式で連番重複が拒否されること、Streams経由でPostgreSQL投影が更新されることを確認。
- M5: 移行後、同RESTシナリオが5.x上で同結果になる回帰確認。差分を移行ドキュメントに反映。
- M6: ウォレットで 付与→利用→失効、残高≥0違反の拒否、失効の時間駆動発火、会計負債ビューの整合を確認。

## 未決・後続で判断（ブロッカーではない）
- PHPフレームワークの具体選定（M7）。
- M4のStreams消費の実装形態、5.x移行時の追跡トークン/グローバル順序の作り込み範囲（実ドキュメント確認のうえM4/M5で確定）。
