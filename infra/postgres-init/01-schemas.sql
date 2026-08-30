-- 書き側（イベントストア）と読み側（リードモデル）をスキーマで分ける（docs/decisions.md H36）。
--
-- 分ける理由は M4 で効く: イベントを DynamoDB へ剥がすとき、どのテーブルが Axon 由来かを
-- 目で選り分けずに済む。テーブル自体は ddl-auto: update で Hibernate が作る。
CREATE SCHEMA IF NOT EXISTS eventstore;
CREATE SCHEMA IF NOT EXISTS readmodel;

GRANT ALL ON SCHEMA eventstore TO warehouse;
GRANT ALL ON SCHEMA readmodel  TO warehouse;
