# event-sourcing-handson

イベントソーシング + CQRS + DDD を、**ドメイン分析から実アプリの落とし込みまで一気通貫**で学ぶためのハンズオン・リポジトリ。
Axon Framework と DynamoDB を題材に、複数ドメイン・複数言語で連作的に手を動かしながら知見を蓄積することを目的とする。

> 学習・知見共有用のサンプルです。詳細な計画は [`docs/plan.md`](docs/plan.md) を参照。

## 目的

「イベントストーミングによるドメイン分析 → 戦略/戦術設計 → 実アプリへの落とし込み」を、
イベントソーシング + AWS(DynamoDB + DynamoDB Streams) 制約下で体験・言語化する。
履歴を追うことがメリットになるドメインを題材にし、ES/CQRS の効きどころを実感する。

## 学習の流れ（題材とバージョン戦略）

学習曲線を「既知 → 差分 → 応用」に設計している。

1. **第1弾: スマート倉庫在庫管理** を **Axon Framework 4.x** で構築
   複雑な集約境界（コア = 在庫引当 / Stock Allocation）で DDD 分析を鍛える。
2. **4.x → 5.x へアップグレード**
   公式移行ガイド駆動の差分学習（非同期モデル / AppendCondition・Dynamic Consistency Boundary）。
3. **第2弾: ポイントウォレット** を最初から **Axon Framework 5.x** で構築
   ES/CQRS 機構の復習＋インフラ理解の定着。将来の他言語（PHP）比較のベースにする。

イベントストアは Axon 公式非対応の **DynamoDB を独自実装で連携**する（具体的な実装方式は別途検討）。
各題材とも「ドメイン先行 → 自作 DynamoDB エンジン後付け」の順で進め、分析・設計を最難関インフラに人質に取らせない。

## 技術スタック

| 領域 | 採用 |
|---|---|
| 言語 / ビルド | Java 21 (Corretto) / Gradle (Kotlin DSL) + Wrapper |
| フレームワーク | Axon Framework 4.x（→ 5.x）/ Spring Boot 3.x |
| 読みモデル (Query 側) | PostgreSQL |
| イベントストア | 組み込み（初期）→ DynamoDB 独自実装（本命） |
| ローカル AWS | LocalStack（DynamoDB + DynamoDB Streams）/ AWS SDK for Java v2 |

※ 本番は AWS 稼働を想定。**Axon Server は使わず** OSS の Axon Framework のみで完結する。

## リポジトリ構成

```
event-sourcing-handson/
├── CLAUDE.md                       # プロジェクトのステアリングの背骨
├── docs/                           # plan.md ＋ 分析・設計の成果物（Markdown + Mermaid）
├── .claude/                        # ガードレール（Rules / Hooks / Skills / SubAgents）
├── infra/                          # docker-compose（LocalStack, PostgreSQL）※M0で追加
├── warehouse-domain/               # 集約・コマンド・イベント（純ドメイン）※M3で追加
├── warehouse-command/              # コマンドハンドラ・Axon 設定
├── warehouse-query/                # プロジェクション・読みモデル・クエリハンドラ
├── warehouse-eventstore-dynamodb/  # 自作 AbstractEventStorageEngine ※M4で追加
└── warehouse-app/                  # Spring Boot 起動・REST API
```

## ステアリング機構（`.claude/`）

開発の品質を保つガードレール／フィードバック機構を先に整備している
（Claude Code の Rules / Hooks / Skills / SubAgents を活用）。

- **Rules** `.claude/rules/` … ES / 集約設計 / CQRS / ユビキタス言語 / テストの遵守ルール
- **Skills** `.claude/skills/` … `event-storming`（分析）/ `add-aggregate` / `add-projection`（雛形生成）
- **SubAgents** `.claude/agents/` … `es-domain-reviewer`（ルール準拠レビュー）/ `es-poc-tester`（REST 実地確認）/ `axon-docs-researcher`（バージョン差の一次ドキュメント確認）
- **Hooks** `.claude/settings.json` … 編集時コンパイル / `Stop` 時に `./gradlew test` ＋ レビューゲート

## 現在の状況

- ✅ 計画（[`docs/plan.md`](docs/plan.md)）とステアリング機構の整備
- ⬜ M0: 足場（Gradle マルチモジュール雛形 / docker-compose）
- ⬜ M1〜: 倉庫の分析・設計・実装（以降、上記ロードマップに沿って進行）

## セットアップ / 動作確認（M0 以降）

```bash
docker compose -f infra/docker-compose.yml up -d   # LocalStack + PostgreSQL
./gradlew test                                     # 全テスト
./gradlew :warehouse-app:bootRun                   # アプリ起動（ポート 8080 想定）
```

## 参考

- Axon Framework: <https://github.com/AxonIQ/AxonFramework> / <https://docs.axoniq.io/axon-framework-reference/>
