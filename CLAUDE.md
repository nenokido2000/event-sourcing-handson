# イベントソーシング学習PoC（倉庫在庫管理 / Axon Framework + DynamoDB）

## 目的
ドメイン分析（イベントストーミング）→ 戦略/戦術設計 → 実アプリへの落とし込みを、
イベントソーシング + CQRS + AWS(DynamoDB + DynamoDB Streams) 制約下で一気通貫に学ぶ、
再現性のある学習・知見共有用サンプル。詳細な計画は [`docs/plan.md`](docs/plan.md) を参照。

## 題材とバージョン戦略
- **第1弾: スマート倉庫在庫管理**（複雑な集約境界で分析を鍛える）を **Axon 4.x** で構築。
- 完成後 **4.x → 5.x へアップグレード**（差分学習）→ **第2弾: ポイントウォレット**を最初から **5.x** で構築。
- イベントストアは **DynamoDB を独自実装で連携**（Axon公式非対応。具体的な実装方式は別途検討）。**Axon Server は使わない**（OSS側のみ）。

## 技術スタック
- Java 25 (Corretto / 現行LTS) / **Gradle (Kotlin DSL) + Wrapper**
- Axon Framework 4.x（**4.13+ = Spring Boot 4 対応版**）+ Spring Boot 4.1（`axon-spring-boot-starter`）
  - ※ Spring Boot 4 は Jackson 3 がデフォルト。Axon の Serializer 設定に注意（詳細は `docs/plan.md`）
- 読みモデル: PostgreSQL / ローカルAWS: DynamoDB Local（amazon/dynamodb-local。DynamoDB + Streams）/ AWS SDK for Java v2
  - ※ LocalStack はライセンス必須化（2026-03）につき不採用。DynamoDB Local は無料・アカウント不要で DynamoDB + Streams に対応。

## モジュール構成
- `warehouse-domain` … 集約・コマンド・イベント（純ドメイン）
- `warehouse-command` … コマンドハンドラ・Axon設定
- `warehouse-query` … プロジェクション・読みモデル・クエリハンドラ
- `warehouse-eventstore-dynamodb` … 自作 `AbstractEventStorageEngine`（M4で追加）
- `warehouse-app` … Spring Boot起動・REST API
- `infra` … docker-compose（DynamoDB Local, PostgreSQL）/ `docs` … 分析・設計成果物

## ステアリング（この各機構を使うこと）
- **Rules** `.claude/rules/` … 設計・実装の遵守ルール。コードを書く前後に必ず参照する。
  - `event-sourcing.md` / `aggregate-design.md` / `cqrs-projection.md` / `ddd-ubiquitous-language.md` / `testing.md`
- **Skills** `.claude/skills/`
  - `event-storming` … ドメイン分析を進め `docs/` に成果物を書き出す（分析フェーズで使用）
  - `add-aggregate` … 集約一式（コマンド/イベント/集約/Fixtureテスト）を規約準拠で雛形生成
  - `add-projection` … 読みモデル＋プロジェクション＋クエリハンドラ＋テストを雛形生成
- **SubAgents** `.claude/agents/`
  - `es-domain-reviewer` … `.claude/rules/` 準拠をレビュー（Stopフックのゲート。`run_in_background:false` で呼ぶ）
  - `es-poc-tester` … 起動済みアプリに REST を叩き実地確認（M4以降は DynamoDB 行も検査）
  - `axon-docs-researcher` … Axon リファレンス/apidocs を参照しバージョン差のAPI疑問を確認（特に5.x）
- **Hooks** `.claude/settings.json`
  - PostToolUse(Edit|Write, `*.java`) → コンパイル / PreToolUse(Bash) → `rm -rf` ブロック
  - SubagentStop → 通知音 / Stop → `./gradlew test` + レビューゲート
  - ※ `gradlew` 未生成の間は gradle 系チェックは no-op（M0で有効化）

## ドメインの要点（ユビキタス言語）
- コアサブドメイン = **在庫引当（Stock Allocation）**。集約 `InventoryItem`、不変条件 `available = onHand - allocated ≥ 0`。
- コマンドは命令形（`ReceiveStock` 等）、イベントは過去形（`StockReceived` 等）。用語集は `docs/` を正とする。

## 動作確認方法（M0以降に充実）
```bash
docker compose -f infra/docker-compose.yml up -d   # DynamoDB Local + PostgreSQL
./gradlew test                                     # 全テスト
./gradlew :warehouse-app:bootRun                   # アプリ起動（ポート8080想定）
```
