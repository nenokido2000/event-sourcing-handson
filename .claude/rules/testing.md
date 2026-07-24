# テスト

- **開発は TDD＋ATDD の二重ループで進める**（M3以降の実装フェーズ全体）。
  - 内側=TDD: ドメイン（集約・値オブジェクト）は**テストを先に書いて失敗させ**（Red）→最小実装（Green）→整理（Refactor）。プロダクションコードは失敗するテストを緑にするためだけに書く。
  - 外側=ATDD: 受入基準を **Gauge の Markdown Spec（`specs/`）** に先に書き、**Playwright request API** でヘッドレスに REST を叩いて検証する（ブラウザ/UIは用意しない）。Spec は生きたドキュメントとして保つ。
  - 順序: 受入 Spec を Red にする → 内側の TDD で駆動して実装 → 受入 Spec が緑になったらスライス完成。
- 集約ごとに Axon Test Fixture（`AggregateTestFixture`）で Given-When-Then を書く。`given(過去イベント...).when(コマンド).expectEvents(...)` / `expectException(...)` を最低限カバーする。
- 不変条件は必ず異常系テストで守る（例: 在庫超過の引当は `expectException` で拒否し、イベントを発行しないこと）。
- プロジェクションは「イベント入力 → 読みモデル状態」のテストを書く。冪等性（同一イベントの二重適用）も検証する。
- DynamoDBイベントストア（M4）は DynamoDB Local / Testcontainers を使った結合テストで、追記・楽観ロック（連番重複拒否）・アグリゲート読み出しを検証する。
- テストはドメインの言葉で記述する（コマンド/イベント名がそのまま表れる）。実装詳細ではなく振る舞いを検証する。
