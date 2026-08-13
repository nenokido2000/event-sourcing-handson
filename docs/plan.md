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
- リードモデル(Query側): **PostgreSQL**（ローカル=Docker / AWS=RDS for PostgreSQL）
  - ※当初はスタックの既定値だったが、M2 で**引当可能在庫ビューのクエリの形**（best-fit 検索＋2属性ソート）から
    改めて導出し直して確定した。DynamoDB との比較と、FK を置かない理由は
    [`decisions.md`](decisions.md#h29-リードモデルのストア選定とキー設計)（H29）。
- イベントストア: 段階導入（M3=組み込み → M4=DynamoDB自作）
- ローカルAWS: **DynamoDB Local**（`amazon/dynamodb-local`。DynamoDB + DynamoDB Streams）。AWS SDK for Java v2。※LocalStackはライセンス必須化（2026-03、アカウント+auth token必須）につき不採用。DynamoDB Localは無料・アカウント不要で本PoCに必要なDynamoDB+Streamsを満たす。
- **テスト/開発手法**:
  - TDD: JUnit 5 ＋ Axon `AggregateTestFixture`（集約）/ 値オブジェクトは素の JUnit。**テストを先に書く**運用。
  - ATDD: **Gauge**（`gauge-java` プラグイン、仕様は Markdown）＋ **Playwright for Java**（`APIRequestContext` で REST をヘッドレス実行。ブラウザ/UIは用意しない）。Spec は生きたドキュメントとして `specs/` に置く。
- **本番インフラ / IaC（M8）**: **Terraform**。AWS 構成 = **ECS Fargate**（Spring Boot コンテナ）＋ **ALB** ＋ **RDS for PostgreSQL**（リードモデル）＋ **DynamoDB＋DynamoDB Streams**（実イベントストア）＋ **Lambda**（Streams 消費→RDS 投影）。付随: ECR / VPC・サブネット / IAM / Secrets Manager / CloudWatch Logs。※学習用のため未使用時は `terraform destroy` で撤去する前提。

## アーキテクチャ方針（倉庫）
- **境界づけられたコンテキスト**: 在庫 Inventory（コア）/ 入荷 Receiving / 出荷 Fulfillment / 棚卸 Stocktaking。上流に 受注 Ordering（薄い外部トリガ）。
- **コアサブドメイン**: 在庫引当（Stock Allocation）。
- **集約・コマンド・イベント**: **正は [`tactical-design.md`](tactical-design.md)**（状態・受付ゲート・例外）と
  [`ubiquitous-language.md`](ubiquitous-language.md)（用語）。**ここには写さない**
  （[`.claude/rules/doc-consistency.md`](../.claude/rules/doc-consistency.md) の「同じ情報を2か所に持たない」）。
  - ※ M0 時点の暫定一覧をここに置いていたが、M2 の確定（`ReceiveStock` は入荷集約・`ShipStock` は出荷集約に属し、
    在庫が受けるのは `PlaceStock` / `IssueStock`）と食い違ったまま残っていたため撤去した。
  - コアだけ再掲: 在庫 `InventoryItem`（SKU × ロケーション）の不変条件 **引当可能 = 手持在庫 − 引当済 ≥ 0**。
    強制点は引当コマンドの受付ゲート（[H12](decisions.md#h12-実地値が引当済を下回る棚卸調整)）。
  - ※ Cosmic Python の allocation 例に近い、意図的に非自明な集約境界。
- **CQRS（読み書き分離）**:
  - 書き込み側 = Axon 集約（イベントソース）。
  - 読み側プロジェクション: 用途ごとに分ける（万能ビューを作らない）。**顔ぶれの正は
    [`tactical-design.md`](tactical-design.md) のリードモデル節**（M2 で確定。棚卸の分析で2種増えた）。
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
  warehouse-query/               # プロジェクション・リードモデル・クエリハンドラ
  warehouse-eventstore-dynamodb/ # M4で追加: AbstractEventStorageEngine のDynamoDB実装
  warehouse-app/                 # Spring Boot起動・REST API
  warehouse-atdd/                # M3で追加: Gauge のステップ実装（Java）＋ Playwright(request) ランナー
  specs/                         # M2で追加: Gauge の Markdown Spec（受入基準＝生きたドキュメント。H31）
  gradlew, gradle/wrapper/...    # Wrapper同梱
```

## マイルストーン（分析最優先・段階的・各段で成果物を残す）
- **M0 — 足場**: `git init`、Gradleマルチモジュール雛形、`infra/docker-compose.yml`(DynamoDB Local+Postgres)、README。（→ ここでステアリングのゲートが有効化）
- **M1 — 倉庫の戦略設計（分析成果物）**: イベントストーミング（Big Picture→Process→Design）を `docs/` にMermaidで記録。BC/コンテキストマップ/コアサブドメイン特定。← **最重視**（`event-storming` スキル活用）
- **M2 — 倉庫の戦術設計（成果物）**: 集約境界・コマンド/イベント/ポリシー・不変条件・ユビキタス言語を `docs/` に整理。**この段でATDDの受入シナリオも洗い出す**（[H31](decisions.md#h31-受入シナリオの置き場と粒度) で「散文ではなく最初から `specs/` に Gauge Spec の形で書く」に変更。**M2 は文面まで・ステップ実装は M3**）。成果物は [`../specs/`](../specs/)。
  - **進め方＝1集約1スライス**（M1 と同じ刻み方）。M1 で洗い出した4集約＋ポリシー/リードモデルをそのまま①〜⑤とし、コア（在庫）から順に型レベルまで確定させる。
  - **進捗表は [`tactical-design.md`](tactical-design.md) の冒頭**（スライスごとの確定日）。決定は [`decisions.md`](decisions.md) に H番号で記録する。
- **M3 — 倉庫実装 (Axon 4.x / 組み込みストア)**: M2 で確定した**4集約すべて**を動かす + リードモデル + REST API。**TDD＋ATDDの二重ループで実装**する。
  - **刻み方**: (3-a) 受入→引当→出荷の**動く垂直スライス**（P1・P2・P3・P6）→ (3-b) **棚卸**（集約④・P4・サーガ P5・棚卸差異／干渉ビュー）。M2 の①〜⑤と同じく、薄く通してから足す。
  - **棚卸を M3 に含める理由**（M0 時点のこの行は「受入→引当→出荷」だった）: 棚卸は BC としては当初からあったが、
    **M2 の分析で最も深い領域になった**（[H18](decisions.md#h18-棚卸は数える対象の母集合を持つか)〜[H23](decisions.md#h23-棚卸の重複開始)・[H27](decisions.md#h27-棚卸凍結サーガの状態と終わり方)）。
    **本PoC唯一の Saga（P5）と、唯一イベントから再構築できないビュー（棚卸干渉）は棚卸にしか無く**、
    実装しなければ設計が確かめられないまま残る。
  - 外側(ATDD): **Spec の文面は M2 で書き終えている**（[`../specs/`](../specs/)）。M3 では `warehouse-atdd` に Playwright(request) で REST を叩くステップ実装を用意して緑にしていく。**まず `harness` タグの1本（各 spec のハッピーパス）でハーネスを立ち上げ**、残りのシナリオはドメインの形が見えてから緑にする（本丸を先に固める）。
  - 内側(TDD): 集約・値オブジェクトを Axon Fixture / JUnit で**テスト先行**（Red→Green→Refactor）。不変条件 `available≥0` の異常系も先に書く。
  - 内側が揃うと外側の受入 Spec が緑に到達 → 垂直スライス完成。この二重ループを以降のMでも踏襲する。
  - ⚑ **この段の前後で「ガード整備 #2（TDD/ATDD遵守ゲート）」を実施**（skill の test-first 既定化・レビュアーのチェックリスト追加・Stop フックのテスト不在チェック）。詳細は「ガード / 品質ゲートの整備」節。
- **M3+ — ドメイン改修シナリオ（任意 / オプショナル）**: 最初の動くスライス（M3）完了後に、「特定ドメインへの**ビジネス要求変更**」を1つ置き、実務に即した開発プロセス（ATDD で Spec 先行 → TDD で駆動）で改修する。**ES/CQRS の旨味は変更に直面して初めて出る**（不変イベントを保ったまま後方互換・履歴活用）ため、これは (A) 概念体得の最深部を兼ねる。
  - **位置づけ**: いつ落としても破綻しない任意ステップ。既存の `InventoryItem` 集約を舞台にし、**新BCは増やさず「よく選ばれた1変更」に絞る**。組み込みストア期（M3直後）に置き、改修をドメイン/ESの純粋問題に隔離する。同じ改修は M4（DynamoDB）/ M5（5.x）で**再演**すると「同じ変更が別インフラでどう効くか」まで見える（M5 の 4.x→5.x は"プラットフォーム変更"の従兄弟）。
  - ✅ **シナリオ確定 = ⑥品質等級の導入**（2026-08-13 / [H32](decisions.md#h32-m3-改修シナリオの選定)）。次点は⑦＋H5。以下は選定に使った候補の記録（どれと比べて選ばれたかを残す）。
  - **選定基準**: ES 特有の筋肉を突く変更を選ぶ（一般的なフィールド追加＝CRUD練習に留めない）。候補と鍛える筋肉: ①新プロジェクション追加（履歴から再構築＝リードモデル使い捨て）②不変条件/ポリシー変更（引当ポリシーの現実的な揺らぎ。**具体候補=H11「棚卸中の引当をどこまで許すか」**——本来は実績データに基づく数値判断で、PoCでは当面「引当は通す＋P2で引当先を後回し」。実績が出たら見直す、という*まさに実務で起きる形*の変更）③**イベントのスキーマ進化（リビジョン＋アップキャスタ、旧イベントは書き換えない）** ④補正/打ち消しイベント（削除せず訂正）。**既定候補=③スキーマ進化（アップキャスタ）**。ES の「削除しない・後方互換」を最も鋭く突くため。
    ⑤**出荷のバッチ化**（ウェーブ／配送便単位。M1で H9 を注文単位に確定した際の積み残し）。実務の多数派はバッチだが、PoCでは注文単位で始め、後から**既存イベントを書き換えずに新集約＋ポリシーを足す**形で導入できる＝「動くモデルへの概念追加」の実演。※新BCは増やさない縛りに対しては 出荷（Fulfillment）内での追加なので適合。
    ⑥**品質等級の導入**（③スキーマ進化の具体化として最有力。[H13](decisions.md#h13-ユニットを個体識別するか) から派生）。「代替可能だった在庫に、後から等級（標準品／格下げ品）が生まれる」という要求変更。**既存の `StockPlaced` は等級を持たないので書き換えず、アップキャスタで「等級の記録なし＝標準等級」として読む**／引当がキー単位に割れるため**コアの不変条件に直接触る**／リードモデルは履歴から再構築すれば等級別ビューが手に入る＝**使い捨て可能性の実演**。等級を在庫キー `(Sku, 等級, LocationId)` に畳む形に留めれば**集約は増えない**（個体識別まで踏み込むとコアが集合演算になり本PoCの目的から外れる → H13 の判別基準）。
    ⑦**ピッキング済み出荷の取消**（[H16](decisions.md#h16-欠品したまま終わる出荷の終わらせ方) の積み残し）。現状は「1件でもピッキング済みなら取り消せない」＝棚から取った物を棚へ戻す工程がモデルに無いため。導入には `StockReturned`（棚への戻し）相当が要り、**[H5](decisions.md#h5-ロケーション間の在庫移動)（ロケーション間移動）と同型の2集約またぎ**になる。H5 とセットで扱うと1つの改修で2つの穴が埋まる。
  - **急所は集約識別子**（[H32](decisions.md#h32-m3-改修シナリオの選定)）。等級を在庫キーに入れると既存ストリームの `aggregateIdentifier` が変わるが、アップキャスタが変換できるのは**ペイロードだけ**。畳み方は M3+ 着手時に決める。
  - **ATDD の勘所**: 要求変更＝Spec を先に足す/変える → **既存 Spec は緑のまま（回帰の安全網）**、新 Spec が変更を駆動する。改修時こそ ATDD が最も効く。
- **M4 — 自作DynamoDBイベントストア (4.x)**: `AbstractEventStorageEngine` をDynamoDBで実装。投影をDynamoDB Streams駆動へ切替。DynamoDB Localで一気通貫。← AWS制約を満たす山場。
- **M5 — 倉庫を Axon 5.x へアップグレード**: 公式移行ガイド駆動の**差分作業**。(5-a) ドメイン/アプリを5.xへ（イベントストアは5.x組み込みへ一旦フォールバック）→ (5-b) 自作DynamoDBエンジンを5.x SPI(非同期/AppendCondition・DCB)へ移植。差分は `docs/axon4-to-5-migration.md` に記録。
- **M6 — ウォレットを最初から 5.x で構築**: 集約=会員ウォレット, 不変条件=残高≥0, 失効=時間駆動, 付与残高/会計負債でCQRS。復習＋インフラ理解の定着。
- **M7 — 他言語PoC(PHP)向け情報整理**: ウォレットを共通題材に、PHPフレームワーク選定基準（DDD/CQRS/ES適性・世界的普及度）を調査整理。候補メモ（例: Ecotone / Prooph / EventSauce）。**具体選定はこの段で判断**。
- **M8 — AWS実環境へデプロイ（Terraform / IaC）**: 成果物 = **最終5.xの倉庫**を1つ本番化する（前提: M5完了。M6/M7とは独立に着手可）。
  - **IaC**: `infra/terraform/` に AWS 構成を Terraform で記述（モジュール分割の目安: `network`(VPC/サブネット/SG) / `data`(DynamoDB＋Streams, RDS PostgreSQL) / `compute`(ECR, ECS Fargate サービス, ALB) / `streams`(Lambda＋イベントソースマッピング) / IAM・Secrets Manager・CloudWatch）。tfstate はまず local、必要なら S3＋DynamoDB ロックへ。
  - **アプリ側の本番化**: Spring プロファイル（`local`/`aws`）で DynamoDB エンドポイント・資格情報・DB接続を切替。コンテナを ECR へ push。
  - **Streams 消費の置換**: ローカル(M4)の「素の SDK v2 ポーリング」を、AWS では **DynamoDB Streams → Lambda → RDS 投影**に置換（本番パターンへ寄せる）。両者が同じ投影結果を作ることを確認。
  - ⚑ **着手前に「ガード整備 #3（デプロイ/クレデンシャル本番ガード）」を実施**（SSO/OIDC＋Secrets Manager＋S3暗号化リモートstate、CIのgitleaks＋push protection）。詳細は「ガード / 品質ゲートの整備」節。
  - **成果物**: `docs/aws-deploy.md`（構成図・手順・コスト注意・`terraform destroy` での撤去手順）。

## 検証（各段のエンドツーエンド確認）
- M3-a: REST で `ReceiveStock`→`AllocateStock`→`ShipStock` → プロジェクション照会 → `available=onHand-allocated` が保たれ `StockLedgerView` に全履歴が並ぶ。過剰引当/出荷が不変条件で弾かれる。
- M3-b: `StartStocktake`→`CountStock`→`CloseStocktake` → 対象在庫が凍結・解凍され（サーガ P5）、差異ビューに帳簿値と実地値が並ぶ。**凍結中の格納/払出が拒否され、棚卸干渉ビューに行が積まれる**（[H22](decisions.md#h22-凍結中に拒否された在庫反映の行き先)）。
- M4: `docker compose up` → 同シナリオ → DynamoDB Localに `aggregateIdentifier/sequenceNumber` 行が追記され条件式で連番重複が拒否されること、Streams経由でPostgreSQL投影が更新されることを確認。
- M5: 移行後、同RESTシナリオが5.x上で同結果になる回帰確認。差分を移行ドキュメントに反映。
- M6: ウォレットで 付与→利用→失効、残高≥0違反の拒否、失効の時間駆動発火、会計負債ビューの整合を確認。
- M8: `terraform apply` で AWS に一式構築 → 同RESTシナリオ（受入→引当→出荷）を**本番の Gauge Spec（環境変数でエンドポイント差替）**で緑にする → 実 DynamoDB に追記・楽観ロックが効き、Streams→Lambda→RDS の投影が更新されることを確認 → `terraform destroy` で撤去できることまで確認。
- ATDD（全M共通）: `specs/` の Markdown Spec が受入基準の正。ローカルでは `docker compose up` 済みアプリに、AWSでは ALB エンドポイントに、同じ Spec を向けて緑を確認する。
- M3+（任意）: 改修シナリオ実施後、**既存の受入 Spec/Fixture テストが緑のまま**（回帰なし）で新 Spec が緑になること、既定候補③なら**旧リビジョンのイベントがアップキャスタ経由で正しく読めること**（旧イベントは書き換わっていないこと）を確認。

## ガード / 品質ゲートの整備（段階導入・忘備）

機密混入と TDD/ATDD 遵守を「機構」で担保する。原則: **決定的に検出できるもの（機密混入・テスト不在/失敗）は決定的機構（pre-commit / hook / CI）で強制。プロセス（test-first 等）は skill 既定＋レビューで誘導**（強制不能なものを hook で強制しようとしない）。認知負荷(A優先)を考え、安い順・必要になる直前で足す。

> **#番号は追加順の安定した ID** であり、実施順ではない（節内から `#3` のように参照するため振り直さない）。
> 実施時期は各項の見出しの括弧（済 / M3 前後 / M8 直前）で読む。

- **#1 機密混入ガード（済 / 2026-07-25 導入）**: `.gitignore` に Terraform state/tfvars・`.env`・秘密鍵等を追加。`gitleaks` を **pre-commit フック（`.githooks/pre-commit`, `core.hooksPath=.githooks`）** で走らせ、ステージ差分の機密を検出しコミットを停止（fail-closed）。手順は `docs/setup.md` 4.5。
- **#2 TDD/ATDD 遵守ゲート（M3 前後で実施）**:
  - `add-aggregate` skill を **test-first の既定動作**に（①失敗するFixtureを生成→②`gradlew test`で赤を見せる→③最小実装で緑→④整理）。`add-projection` も同様。
  - **レビューゲート（es-domain-reviewer）のチェックリストに TDD/ATDD 項目を追加**（各振る舞いにテストがあるか／実装詳細でなく振る舞いを検証しているか／不変条件違反の異常系を `expectException` しているか／受入 Spec が存在するか）。
  - 既存 **Stop フック（`./gradlew test`＋レビューゲート）**に軽い追加: 新規 `*Aggregate.java` に対応する `*Test.java` が無ければ block（＝*テスト不在*を弾く。test-first そのものは機械検証不能なので代理指標）。
  - ATDD の Gauge/Playwright はアプリ起動が要り重いので Stop フックには載せず **CI 側**で回す（下記 #3 と合流）。ローカルは Spec 存在チェック程度に留める。
- **#3 デプロイ/クレデンシャルの本番ガード（M8 直前で実施）**:
  - **構造的回避**（最優先）: AWS **SSO/OIDC の短命クレデンシャル**を使い静的キーを作らない。RDS パスワード等は **Secrets Manager / SSM**。**S3 リモート state（暗号化＋バケット非公開）＋ DynamoDB ロック**にして tfstate を repo に落とさない（M8 の「local か S3 か」はこの方針で S3 に倒す）。
  - **CI バックストップ**: GitHub Actions に **gitleaks ジョブ**（`--no-verify` 抜け対策）＋ **GitHub secret scanning / push protection 有効化**。ATDD 全 Spec もここで実行。
  - （任意）Claude PreToolUse hook で `Edit/Write(*.tf)`・`Bash(git commit)` 時に速報スキャン（本命は pre-commit + CI、これは速報の補助）。
- **#4 ドキュメント整合ガード（済 / 2026-08-09 導入 ← M2 途中で必要になったため前倒し）**: `docs/` の肥大化に伴い「手で書いた値が他所とズレる」腐り方が実際に複数回発生したため導入。ルールは [`.claude/rules/doc-consistency.md`](../.claude/rules/doc-consistency.md)。
  - **構造で消す**（コード不要・最優先）: ①**導出できる値を書かない**（集計値「ポリシーは5本」・採番「次は H13〜」）②**同じ情報を2か所に持たない**（M2 スライス進捗の正は `tactical-design.md` 冒頭表のみ。README はマイルストーン粒度＋リンク）。
  - **決定的チェック**（`scripts/check-docs.py` / python3 のみ・1秒未満）: ①`decisions.md` の `## Hn` 見出しと一覧表の行が集合一致するか ②`](file.md#anchor)` が実在見出しに解決するか ③`ubiquitous-language.md` 定義済みポリシー番号が波及先文書に現れるか／未定義番号を参照していないか。**pre-commit（#1 と同じ場所）と Stop フックの両方**で実行。
  - **機械化しないもの**: 意味の矛盾（新しい決定が既存記述と噛み合わない）は非決定的なのでレビュー（`es-domain-reviewer` ＋人）に寄せる。#2 と同じ「決定的なものだけ機構で強制」の線引き。

## 未決・後続で判断（ブロッカーではない）
- PHPフレームワークの具体選定（M7）。
- M4のStreams消費の実装形態、5.x移行時の追跡トークン/グローバル順序の作り込み範囲（実ドキュメント確認のうえM4/M5で確定）。
- M8 の細部: tfstate を local のままにするか S3＋ロックへ上げるか / RDS を単一AZ最小構成にするか（コスト最優先） / ALB を公開するか制限するか（学習用なので最小権限・最小公開で）。着手時に確定。
- Gauge/Playwright(Java) の具体バージョンと Gradle 組み込み方（`warehouse-atdd` を通常の test タスクと分けるか、専用タスクにするか）。M3着手時に確定。
