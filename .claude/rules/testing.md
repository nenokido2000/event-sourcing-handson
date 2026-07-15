# テスト

- 集約ごとに Axon Test Fixture（`AggregateTestFixture`）で Given-When-Then を書く。`given(過去イベント...).when(コマンド).expectEvents(...)` / `expectException(...)` を最低限カバーする。
- 不変条件は必ず異常系テストで守る（例: 在庫超過の引当は `expectException` で拒否し、イベントを発行しないこと）。
- プロジェクションは「イベント入力 → 読みモデル状態」のテストを書く。冪等性（同一イベントの二重適用）も検証する。
- DynamoDBイベントストア（M4）は LocalStack / Testcontainers を使った結合テストで、追記・楽観ロック（連番重複拒否）・アグリゲート読み出しを検証する。
- テストはドメインの言葉で記述する（コマンド/イベント名がそのまま表れる）。実装詳細ではなく振る舞いを検証する。
