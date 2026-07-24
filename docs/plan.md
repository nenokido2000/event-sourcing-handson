# イベントソーシング学習PoC 計画（倉庫 → ウォレット / Axon 4.x → 5.x → PHP / AWS本番デプロイ）

> このドキュメントは実行計画。ES開発本体（M0〜M8）は段階的に進める。
> ステアリング機構（`CLAUDE.md` / `.claude/`）は先行して整備済み。
> 実装フェーズは **TDD（ドメイン先行）＋ ATDD（受入仕様先行）の二重ループ**で進め、最終的に **AWS実環境へ Terraform(IaC) でデプロイ**する（M8）。

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
- **開発手法は TDD＋ATDD の二重ループ**（実装フェーズ全体を貫く）:
  - 内側ループ = **TDD（ドメイン先行）**。集約・値オブジェクトは Axon `AggregateTestFixture` の Given-When-Then を**先に書いて失敗させ**（Red）→ 最小実装（Green）→ 整理（Refactor）。特にドメインモデルはハンズオンと合わせて実演する。
  - 外側ループ = **ATDD（受入仕様先行）**。Gauge の Markdown Spec で受入基準（ユビキタス言語のまま）を先に書き、Playwright request API でヘッドレスに REST を叩いて検証する。仕様の明確化を早期に効かせる目的で **M3 から導入**。
- **優先順位ガード（最優先は ES/CQRS 概念の体得）**: 技術要素（TDD/ATDD/IaC）は実務忠実度として価値があるが、あるMでツール整備が本丸（ドメイン・イベント設計）の学習を圧迫しそうなら、**本丸を優先し当該ツールはそのM内で後ろ倒し**にする。ATDDハーネス（Gauge/Playwright）は最初に薄い1本を通して基盤化し、以降は再利用する（毎回作り込まない）。
- 完成後、他言語PoC（PHP + フレームワークTBD）へ展開する情報を整理する。
- **仕上げに成果物（最終5.xの倉庫）を AWS 実環境へデプロイ**し、環境構築とデプロイを **Terraform(IaC)** で再現可能にする（M8）。

### 情報源（一次情報・逐次確認する）
- Axon リポジトリ（正）: <https://github.com/AxonIQ/AxonFramework>（旧 `AxonFramework/AxonFramework` はリダイレクト）
- リファレンス（版別）: <https://docs.axoniq.io/axon-framework-reference/>（4.x / 5.x 切替）
- 4→5 移行: <https://github.com/AxonIQ/AxonFramework/blob/main/axon-5/api-changes/index.md> ＋ 5.x migration ガイド
- 補足: Axon 5系は open-core 分割で「Axon Framework(OSS) / Axoniq Framework(商用寄り=Axon Server連携系)」に分離。**本PoCは Axon Server を使わずOSS側のみで完結**する。

## 環境前提（確認済み 2026-07）
- 作業ディレクトリ `/Users/naokienokido/event-sourcing`。Git未初期化（M0で `git init`）。
- Java 25 (Corretto / 現行LTS) / Docker 29 + Compose v2 / Node 23 / Git あり。
- **未導入**: Maven, Gradle（→ Gradle Wrapper 同梱で解決）, PHP/Composer（→ Docker化）, AWS CLI（→ DynamoDB Local + AWS SDK for Java v2。CLIが要る場面は `aws --endpoint-url http://localhost:8000`）。

## 技術スタック（第1弾・倉庫）
- 言語/ビルド: Java 25（現行LTS） / **Gradle (Kotlin DSL) + Wrapper**
- FW: **Axon Framework 4.x（4.13+ = Spring Boot 4 対応版）** + **Spring Boot 4.1**（axon-spring-boot-starter）
  - Spring Boot 4.1 は2026-06リリースの現行推奨版（公式LTS designation は無い）。3.x系はOSSサポート終了済み（**3.5 が 2026-06-30 EOL**）、OSSアクティブは 4.0（〜2026-12-31）/ 4.1（〜2027-07-31）のみで **4.1 が最長** → 実質4.1一択。
  - **Axon 4 が Spring Boot 4 に対応したのは 4.13 から**（4.12不可）。4.13は「4→5移行の踏み石」版で Spring Boot 4 統合が主眼 → M5の4→5移行がむしろ楽になる。
  - **Spring Boot 4 は Jackson 3 デフォルト**。Axon の JacksonSerializer は元々 Jackson 2 前提だったため、Serializer 設定（Jackson 3 対応 or 明示指定）に注意。JDK 17+ 要件（Java 25 で充足）。
- 読みモデル(Query側): **PostgreSQL**（ローカル=Docker / AWS=RDS for PostgreSQL）
- イベントストア: 段階導入（M3=組み込み → M4=DynamoDB自作）
- ローカルAWS: **DynamoDB Local**（`amazon/dynamodb-local`。DynamoDB + DynamoDB Streams）。AWS SDK for Java v2。※LocalStackはライセンス必須化（2026-03、アカウント+auth token必須）につき不採用。DynamoDB Localは無料・アカウント不要で本PoCに必要なDynamoDB+Streamsを満たす。
- **テスト/開発手法**:
  - TDD: JUnit 5 ＋ Axon `AggregateTestFixture`（集約）/ 値オブジェクトは素の JUnit。**テストを先に書く**運用。
  - ATDD: **Gauge**（`gauge-java` プラグイン、仕様は Markdown）＋ **Playwright for Java**（`APIRequestContext` で REST をヘッドレス実行。ブラウザ/UIは用意しない）。Spec は生きたドキュメントとして `specs/` に置く。
- **本番インフラ / IaC（M8）**: **Terraform**。AWS 構成 = **ECS Fargate**（Spring Boot コンテナ）＋ **ALB** ＋ **RDS for PostgreSQL**（読みモデル）＋ **DynamoDB＋DynamoDB Streams**（実イベントストア）＋ **Lambda**（Streams 消費→RDS 投影）。付随: ECR / VPC・サブネット / IAM / Secrets Manager / CloudWatch Logs。※学習用のため未使用時は `terraform destroy` で撤去する前提。

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
  - M4(DynamoDB期): **DynamoDB Streams → ストリーム消費プロセス(DynamoDB Local) → PostgreSQL投影** に切替（AWS本番パターンに一致。Axon の追跡機構はバイパス）。消費は素のAWS SDK v2ポーリングで組む（KCLのStreams AdapterはDynamoDB Local相手だと癖が出る場合があるため）。

## モジュール構成（Gradle マルチモジュール）
```
event-sourcing/
  docs/                          # plan.md ＋ 分析・設計の成果物（Markdown＋Mermaid）
  .claude/                       # ステアリング（rules/hooks/skills/agents）
  infra/                         # docker-compose(DynamoDB Local, PostgreSQL), 初期化スクリプト
  infra/terraform/               # M8で追加: AWS本番のIaC（VPC/ECS/ALB/RDS/DynamoDB/Lambda/ECR…をモジュール分割）
  warehouse-domain/              # 集約・コマンド・イベント（純ドメイン）
  warehouse-command/             # コマンドハンドラ・Axon設定
  warehouse-query/               # プロジェクション・読みモデル・クエリハンドラ
  warehouse-eventstore-dynamodb/ # M4で追加: AbstractEventStorageEngine のDynamoDB実装
  warehouse-app/                 # Spring Boot起動・REST API
  warehouse-atdd/                # M3で追加: Gauge のステップ実装（Java）＋ Playwright(request) ランナー
  specs/                         # M3で追加: Gauge の Markdown Spec（受入基準＝生きたドキュメント）
  gradlew, gradle/wrapper/...    # Wrapper同梱
```

## マイルストーン（分析最優先・段階的・各段で成果物を残す）
- **M0 — 足場**: `git init`、Gradleマルチモジュール雛形、`infra/docker-compose.yml`(DynamoDB Local+Postgres)、README。（→ ここでステアリングのゲートが有効化）
- **M1 — 倉庫の戦略設計（分析成果物）**: イベントストーミング（Big Picture→Process→Design）を `docs/` にMermaidで記録。BC/コンテキストマップ/コアサブドメイン特定。← **最重視**（`event-storming` スキル活用）
- **M2 — 倉庫の戦術設計（成果物）**: 集約境界・コマンド/イベント/ポリシー・不変条件・ユビキタス言語を `docs/` に整理。**この段でATDDの受入シナリオ骨子（Given-When-Then の言葉）も洗い出す**（実装せず言葉だけ。M3でSpec化）。
- **M3 — 倉庫実装 (Axon 4.x / 組み込みストア)**: 受入→引当→出荷の**動く垂直スライス** + 3プロジェクション + REST API。**TDD＋ATDDの二重ループで実装**する。
  - 外側(ATDD): まず `specs/` に Gauge の Markdown Spec（受入→引当→出荷、過剰引当の拒否…）を書き、`warehouse-atdd` に Playwright(request) で REST を叩くステップ実装を用意 → 失敗させる（Red）。**ハーネスは薄いハッピーパス1本で立ち上げ**、Spec の作り込みはドメインの形が見えてから増やす（本丸を先に固める）。
  - 内側(TDD): 集約・値オブジェクトを Axon Fixture / JUnit で**テスト先行**（Red→Green→Refactor）。不変条件 `available≥0` の異常系も先に書く。
  - 内側が揃うと外側の受入 Spec が緑に到達 → 垂直スライス完成。この二重ループを以降のMでも踏襲する。
- **M3+ — ドメイン改修シナリオ（任意 / オプショナル）**: 最初の動くスライス（M3）完了後に、「特定ドメインへの**ビジネス要求変更**」を1つ置き、実務に即した開発プロセス（ATDD で Spec 先行 → TDD で駆動）で改修する。**ES/CQRS の旨味は変更に直面して初めて出る**（不変イベントを保ったまま後方互換・履歴活用）ため、これは (A) 概念体得の最深部を兼ねる。
  - **位置づけ**: いつ落としても破綻しない任意ステップ。既存の `InventoryItem` 集約を舞台にし、**新BCは増やさず「よく選ばれた1変更」に絞る**。組み込みストア期（M3直後）に置き、改修をドメイン/ESの純粋問題に隔離する。同じ改修は M4（DynamoDB）/ M5（5.x）で**再演**すると「同じ変更が別インフラでどう効くか」まで見える（M5 の 4.x→5.x は"プラットフォーム変更"の従兄弟）。
  - **選定基準**: ES 特有の筋肉を突く変更を選ぶ（一般的なフィールド追加＝CRUD練習に留めない）。候補と鍛える筋肉: ①新プロジェクション追加（履歴から再構築＝読みモデル使い捨て）②不変条件/ポリシー変更（引当ポリシーの現実的な揺らぎ）③**イベントのスキーマ進化（リビジョン＋アップキャスタ、旧イベントは書き換えない）** ④補正/打ち消しイベント（削除せず訂正）。**既定候補=③スキーマ進化（アップキャスタ）**。ES の「削除しない・後方互換」を最も鋭く突くため。
  - **具体シナリオは M2（戦術設計）後に確定**する（分析から自然に浮かぶ変更点を採る。今は置き場所と選定基準のみ固定）。
  - **ATDD の勘所**: 要求変更＝Spec を先に足す/変える → **既存 Spec は緑のまま（回帰の安全網）**、新 Spec が変更を駆動する。改修時こそ ATDD が最も効く。
- **M4 — 自作DynamoDBイベントストア (4.x)**: `AbstractEventStorageEngine` をDynamoDBで実装。投影をDynamoDB Streams駆動へ切替。DynamoDB Localで一気通貫。← AWS制約を満たす山場。
- **M5 — 倉庫を Axon 5.x へアップグレード**: 公式移行ガイド駆動の**差分作業**。(5-a) ドメイン/アプリを5.xへ（イベントストアは5.x組み込みへ一旦フォールバック）→ (5-b) 自作DynamoDBエンジンを5.x SPI(非同期/AppendCondition・DCB)へ移植。差分は `docs/axon4-to-5-migration.md` に記録。
- **M6 — ウォレットを最初から 5.x で構築**: 集約=会員ウォレット, 不変条件=残高≥0, 失効=時間駆動, 付与残高/会計負債でCQRS。復習＋インフラ理解の定着。
- **M7 — 他言語PoC(PHP)向け情報整理**: ウォレットを共通題材に、PHPフレームワーク選定基準（DDD/CQRS/ES適性・世界的普及度）を調査整理。候補メモ（例: Ecotone / Prooph / EventSauce）。**具体選定はこの段で判断**。
- **M8 — AWS実環境へデプロイ（Terraform / IaC）**: 成果物 = **最終5.xの倉庫**を1つ本番化する（前提: M5完了。M6/M7とは独立に着手可）。
  - **IaC**: `infra/terraform/` に AWS 構成を Terraform で記述（モジュール分割の目安: `network`(VPC/サブネット/SG) / `data`(DynamoDB＋Streams, RDS PostgreSQL) / `compute`(ECR, ECS Fargate サービス, ALB) / `streams`(Lambda＋イベントソースマッピング) / IAM・Secrets Manager・CloudWatch）。tfstate はまず local、必要なら S3＋DynamoDB ロックへ。
  - **アプリ側の本番化**: Spring プロファイル（`local`/`aws`）で DynamoDB エンドポイント・資格情報・DB接続を切替。コンテナを ECR へ push。
  - **Streams 消費の置換**: ローカル(M4)の「素の SDK v2 ポーリング」を、AWS では **DynamoDB Streams → Lambda → RDS 投影**に置換（本番パターンへ寄せる）。両者が同じ投影結果を作ることを確認。
  - **成果物**: `docs/aws-deploy.md`（構成図・手順・コスト注意・`terraform destroy` での撤去手順）。

## 検証（各段のエンドツーエンド確認）
- M3: REST で `ReceiveStock`→`AllocateStock`→`ShipStock` → 3プロジェクション照会 → `available=onHand-allocated` が保たれ `StockLedgerView` に全履歴が並ぶ。過剰引当/出荷が不変条件で弾かれる。
- M4: `docker compose up` → 同シナリオ → DynamoDB Localに `aggregateIdentifier/sequenceNumber` 行が追記され条件式で連番重複が拒否されること、Streams経由でPostgreSQL投影が更新されることを確認。
- M5: 移行後、同RESTシナリオが5.x上で同結果になる回帰確認。差分を移行ドキュメントに反映。
- M6: ウォレットで 付与→利用→失効、残高≥0違反の拒否、失効の時間駆動発火、会計負債ビューの整合を確認。
- M8: `terraform apply` で AWS に一式構築 → 同RESTシナリオ（受入→引当→出荷）を**本番の Gauge Spec（環境変数でエンドポイント差替）**で緑にする → 実 DynamoDB に追記・楽観ロックが効き、Streams→Lambda→RDS の投影が更新されることを確認 → `terraform destroy` で撤去できることまで確認。
- ATDD（全M共通）: `specs/` の Markdown Spec が受入基準の正。ローカルでは `docker compose up` 済みアプリに、AWSでは ALB エンドポイントに、同じ Spec を向けて緑を確認する。
- M3+（任意）: 改修シナリオ実施後、**既存の受入 Spec/Fixture テストが緑のまま**（回帰なし）で新 Spec が緑になること、既定候補③なら**旧リビジョンのイベントがアップキャスタ経由で正しく読めること**（旧イベントは書き換わっていないこと）を確認。

## 未決・後続で判断（ブロッカーではない）
- PHPフレームワークの具体選定（M7）。
- M4のStreams消費の実装形態、5.x移行時の追跡トークン/グローバル順序の作り込み範囲（実ドキュメント確認のうえM4/M5で確定）。
- M8 の細部: tfstate を local のままにするか S3＋ロックへ上げるか / RDS を単一AZ最小構成にするか（コスト最優先） / ALB を公開するか制限するか（学習用なので最小権限・最小公開で）。着手時に確定。
- Gauge/Playwright(Java) の具体バージョンと Gradle 組み込み方（`warehouse-atdd` を通常の test タスクと分けるか、専用タスクにするか）。M3着手時に確定。
