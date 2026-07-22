# 環境構築手順（オンボーディング）

このリポジトリを新しく clone した人が、開発・動作確認できる状態になるための手順。
方針はハイブリッド構成（**ビルド/テスト/デバッグはローカル、インフラは Docker**）。

## 1. 必要なもの

### 必須（これだけ入っていれば OK）

| ツール | 用途 | 備考 |
|---|---|---|
| **JDK 25**（Amazon Corretto 推奨 / 現行LTS） | Gradle Wrapper の実行・アプリ実行・IDE の Project SDK | Java 25 固定。**Gradle 本体のインストールは不要**（下記参照） |
| **Docker Desktop**（または Docker Engine + Compose v2） | ローカルインフラ（DynamoDB Local / PostgreSQL） | 使う前に起動しておく |
| **Git** | clone・バージョン管理 | |

### 任意（無くても進められる）

| ツール | 用途 | 代替 |
|---|---|---|
| IntelliJ IDEA | 推奨 IDE（ハンズオン・ステップ実行） | 他の IDE / エディタでも可 |
| AWS CLI | DynamoDB の中身確認 | `aws --endpoint-url http://localhost:8000 ...`。アプリの AWS SDK v2 や IDE でも代替可（M4 で欲しくなったら導入） |
| PostgreSQL クライアント（`psql`） | 読みモデルの確認 | `docker exec warehouse-postgres psql ...` / IntelliJ の Database ツール |

### 入れなくてよいもの（意図的に不要）

- **Gradle 本体** … `./gradlew`（Wrapper）が固定版 Gradle 9.6.1 を自動取得・管理する。バージョンはリポジトリに固定されており再現性がある。
- **Maven / PHP / Composer / Node** … 第1弾（Java/Gradle）では未使用。PHP は M7 で Docker 化して扱う。

## 2. 固定バージョン（再現性の担保）

| 対象 | バージョン | 定義場所 |
|---|---|---|
| Java | 25（toolchain / 現行LTS） | ルート `build.gradle.kts` |
| Gradle | 9.6.1（Wrapper） | `gradle/wrapper/gradle-wrapper.properties` |
| Spring Boot | 4.1.0 | `gradle/libs.versions.toml` |
| Axon Framework | 4.13.2 | `gradle/libs.versions.toml` |
| PostgreSQL | 17（コンテナ） | `infra/docker-compose.yml` |
| DynamoDB Local | `amazon/dynamodb-local`（コンテナ） | `infra/docker-compose.yml` |

> **なぜ LocalStack ではないのか**: LocalStack は 2026-03 に Community 版を終了し、起動にアカウント + auth token が必須化された。本 PoC が必要とするのは DynamoDB + DynamoDB Streams のみで、`amazon/dynamodb-local`（無料・アカウント不要）で充足するため、こちらを採用している。

## 3. 使用ポート

| ポート | サービス |
|---|---|
| 8080 | アプリ（REST API。M3 以降） |
| 8000 | DynamoDB Local |
| 5432 | PostgreSQL |

競合する場合は `infra/docker-compose.yml`（インフラ側）や `warehouse-app/src/main/resources/application.yml`（アプリ側）を調整する。

## 4. セットアップ手順

```bash
# 1) clone
git clone <repository-url>
cd event-sourcing

# 2) JDK が 21 であることを確認
java -version        # => openjdk version "25.x" ...

# 3) インフラ起動（事前に Docker Desktop を起動しておく）
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps       # dynamodb / postgres が Up

# 4) ビルド（初回のみ Gradle 9.6.1 と依存を DL。以降は ~/.gradle キャッシュで高速）
./gradlew build       # Windows は gradlew.bat build

# 5) テスト
./gradlew test

# 6) （M3 以降）アプリ起動
./gradlew :warehouse-app:bootRun                    # http://localhost:8080
```

## 5. 動作確認（期待される結果）

| 確認 | コマンド | 期待値 |
|---|---|---|
| Gradle 版 | `./gradlew --version` | `Gradle 9.6.1` |
| ビルド | `./gradlew build` | `BUILD SUCCESSFUL` |
| DynamoDB Local | `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8000` | `400`（素の GET を弾く応答＝サービス起動中の証拠） |
| PostgreSQL | `docker exec warehouse-postgres pg_isready -U warehouse -d warehouse_read` | `accepting connections` |

読みモデル DB の接続情報（`application.yml` と一致）:
`host=localhost / port=5432 / db=warehouse_read / user=warehouse / password=warehouse`

## 6. IntelliJ IDEA の設定

- **Open** で `settings.gradle.kts` を選択（Gradle プロジェクトとしてインポート）。
- **Project SDK = Corretto 25**（`File → Project Structure → Project`）。
- **Settings → Build Tools → Gradle → "Use Gradle from: `gradle-wrapper.properties`"** に設定（IDE 同梱 Gradle ではなく Wrapper=9.6.1 に揃え、CLI と挙動を一致させる）。
- `JAVA_HOME` は未設定でも Wrapper / IDE が自動検出するため問題ない。

## 7. インフラの停止・後片付け

```bash
docker compose -f infra/docker-compose.yml down      # 停止（ボリューム=データは保持）
docker compose -f infra/docker-compose.yml down -v   # データ（DynamoDB/Postgres）も削除
```

## 8. トラブルシュート

| 症状 | 原因・対処 |
|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop が未起動。起動してから再実行する。 |
| `No matching toolchain` / JDK 25 が見つからない旨のエラー | JDK 25 が未導入。Amazon Corretto 25 を入れる（本プロジェクトは toolchain の自動ダウンロードを有効化していないため、25 をローカルに用意する必要がある）。 |
| ポート競合（8000 / 5432 / 8080） | 既存プロセスを停止するか、compose / `application.yml` のポートを変更する。 |
| 初回 `./gradlew` が遅い | Gradle 配布物と依存の初回 DL。2 回目以降は `~/.gradle` キャッシュで高速化する。 |
