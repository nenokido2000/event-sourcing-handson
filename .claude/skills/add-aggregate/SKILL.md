---
name: add-aggregate
description: 新しいイベントソース集約一式(コマンド・イベント・集約クラス・Fixtureテスト)を、このPoCのルールに沿って一括生成する。「◯◯集約を追加して」「新しい集約を作って」等で使用。既存集約へのコマンド/イベント追加にも使う。
---

# 新規イベントソース集約の追加

Axon の集約を規約準拠で追加する手順。既存の集約(あれば `warehouse-domain/` 配下)をテンプレートとして参照し、同じパターンを踏襲する。作業前に `.claude/rules/` の event-sourcing / aggregate-design / ddd-ubiquitous-language / testing を読むこと。

## 手順

1. 集約名(名詞)、識別子、保持する状態、不変条件、扱うコマンド/イベントをユーザーに確認する。命名は命令形コマンド・過去形イベントに揃える。

2. **コマンド**を作成する(命令形、例: `AllocateStock`)。イミュータブルな値の集合として定義し、対象集約の識別子を含める。

3. **イベント**を作成する(過去形、例: `StockAllocated`)。「起きた事実」のみを持ち、UI/クエリ都合のデータを混ぜない。

4. **集約クラス**を作成する:
   - `@Aggregate`、識別子に `@AggregateIdentifier`。
   - `@CommandHandler`: 不変条件を判断し、満たせば `AggregateLifecycle.apply(event)`、違反なら例外を投げてイベントを発行しない。
   - `@EventSourcingHandler`: 状態遷移(フィールド更新)のみ。判断・副作用を書かない。
   - 他集約はIDで参照する。

5. **Fixtureテスト**を作成する(`AggregateTestFixture`):
   - 正常系: `given(過去イベント...).when(コマンド).expectEvents(...)`
   - 不変条件の異常系: `when(不正なコマンド).expectException(...)` かつイベント非発行を確認。

6. 生成後、`./gradlew compileJava compileTestJava test` を実行して型エラー・テスト失敗がないことを確認する。

7. **コミット前に `es-domain-reviewer` を run_in_background:false で実行**し、ルール準拠(REVIEW_VERDICT: PASS)を確認する。
