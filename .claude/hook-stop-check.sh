#!/bin/bash
# Stop: 応答完了時に (1) ./gradlew test (2) es-domain-reviewer のレビューゲート を検証する。
# Gradle雛形が未生成の間はゲート全体を no-op（M0で有効化）。
INPUT=$(cat)
STOP_HOOK_ACTIVE=$(echo "$INPUT" | jq -r '.stop_hook_active // false')
TRANSCRIPT=$(echo "$INPUT" | jq -r '.transcript_path // empty')
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# gradlew が無ければまだ足場が無いのでスキップ
[ -x "$ROOT/gradlew" ] || exit 0

FAILURES=()

./gradlew test > .claude/stop-check.log 2>&1
if [ $? -ne 0 ]; then
  FAILURES+=("./gradlew test が失敗しています。.claude/stop-check.log を確認し、修正してください。")
fi

# Java変更のレビューゲート（gitリポジトリがある場合のみ）
if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  JAVA_CHANGED=$(git diff --name-only HEAD -- '*.java' 2>/dev/null)
  if [ -n "$JAVA_CHANGED" ] && [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
    VERDICT_JSON=$(jq -c -n -f .claude/review-verdict.jq "$TRANSCRIPT" 2>/dev/null)
    LAST_EDIT=$(echo "$VERDICT_JSON" | jq -r '.lastEdit // -1')
    REVIEW_LINE=$(echo "$VERDICT_JSON" | jq -r '.reviewLine // -1')
    VERDICT=$(echo "$VERDICT_JSON" | jq -r '.verdict // empty')

    if [ "$REVIEW_LINE" -lt "$LAST_EDIT" ]; then
      FAILURES+=("Javaコードが変更されていますが、最新の変更に対して es-domain-reviewer のレビューが実行されていません。es-domain-reviewer を run_in_background:false で実行してください。")
    elif [ "$VERDICT" = "FAIL" ]; then
      FAILURES+=("es-domain-reviewer が Critical 指摘を報告しています。指摘を確認・修正のうえ再度レビューしてください。")
    elif [ "$VERDICT" != "PASS" ]; then
      FAILURES+=("es-domain-reviewer の実行結果(REVIEW_VERDICT)を確認できませんでした。run_in_background:false で実行し、完了を待ってください。")
    fi
  fi
fi

if [ ${#FAILURES[@]} -eq 0 ]; then
  exit 0
fi

REASON=$(printf '%s\n' "${FAILURES[@]}")

if [ "$STOP_HOOK_ACTIVE" = "true" ]; then
  jq -n --arg msg "修正後もチェックに失敗しています:
$REASON" '{systemMessage: $msg}'
  exit 0
fi

jq -n --arg reason "$REASON" '{decision: "block", reason: $reason}'
exit 2
