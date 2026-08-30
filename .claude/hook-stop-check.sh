#!/bin/bash
# Stop: 応答完了時に (1) ドキュメント整合 (2) ./gradlew test (3) es-domain-reviewer のレビューゲート を検証する。
# (2)(3) は Gradle雛形が未生成の間は no-op（M0で有効化）。(1) は分析フェーズから有効。
INPUT=$(cat)
STOP_HOOK_ACTIVE=$(echo "$INPUT" | jq -r '.stop_hook_active // false')
TRANSCRIPT=$(echo "$INPUT" | jq -r '.transcript_path // empty')
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FAILURES=()

# ドキュメント整合チェック（ガード #4 / ルール: .claude/rules/doc-consistency.md）
if command -v python3 >/dev/null 2>&1 && [ -f "$ROOT/scripts/check-docs.py" ]; then
  DOC_OUT=$(python3 "$ROOT/scripts/check-docs.py" 2>&1)
  if [ $? -ne 0 ]; then
    FAILURES+=("ドキュメントに不整合があります。修正してください:
$DOC_OUT")
  fi
fi

# gradlew が無ければまだ足場が無いので、以降（テスト・レビューゲート）はスキップ
if [ ! -x "$ROOT/gradlew" ]; then
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
fi

./gradlew test > .claude/stop-check.log 2>&1
if [ $? -ne 0 ]; then
  FAILURES+=("./gradlew test が失敗しています。.claude/stop-check.log を確認し、修正してください。")
fi

# Java変更のレビューゲート（gitリポジトリがある場合のみ）
#
# 「レビュー済みか」は *ファイルの更新時刻* と *結果が届いた時刻* で判定する。
# transcript 上の Edit/Write を数える方式だと、Bash（sed / heredoc 等）で書き換えた分を取りこぼす。
if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  # 追跡済みの変更に加えて未追跡の .java も見る（新規クラスは git diff に出ない）
  JAVA_CHANGED=$( { git diff --name-only HEAD -- '*.java' 2>/dev/null;
                    git ls-files --others --exclude-standard -- '*.java' 2>/dev/null; } | sort -u )

  if [ -n "$JAVA_CHANGED" ] && [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
    VERDICT_JSON=$(jq -c -n -f .claude/review-verdict.jq "$TRANSCRIPT" 2>/dev/null)
    VERDICT=$(echo "$VERDICT_JSON" | jq -r '.verdict // empty')
    VERDICT_AT=$(echo "$VERDICT_JSON" | jq -r '.verdictAt // empty')

    # 変更された .java のうち最も新しい更新時刻（削除だけなら 0 のまま）
    NEWEST_JAVA=0
    while IFS= read -r f; do
      [ -f "$f" ] || continue
      m=$(stat -f %m "$f" 2>/dev/null || stat -c %Y "$f" 2>/dev/null)
      [ -n "$m" ] && [ "$m" -gt "$NEWEST_JAVA" ] && NEWEST_JAVA=$m
    done <<< "$JAVA_CHANGED"

    REVIEW_EPOCH=0
    if [ -n "$VERDICT_AT" ]; then
      REVIEW_EPOCH=$(date -j -u -f "%Y-%m-%dT%H:%M:%S" "${VERDICT_AT%.*}" +%s 2>/dev/null \
                     || date -u -d "$VERDICT_AT" +%s 2>/dev/null || echo 0)
    fi

    if [ -z "$VERDICT" ]; then
      FAILURES+=("Javaコードが変更されていますが、es-domain-reviewer のレビュー結果を確認できません。es-domain-reviewer を実行し、完了を待ってください（非同期で起動した場合は TaskOutput で結果を受け取ること）。")
    elif [ "$VERDICT" = "FAIL" ]; then
      FAILURES+=("es-domain-reviewer が Critical 指摘を報告しています。指摘を確認・修正のうえ再度レビューしてください。")
    elif [ "$NEWEST_JAVA" -gt "$REVIEW_EPOCH" ]; then
      FAILURES+=("レビュー完了後に Java コードが変更されています。es-domain-reviewer を実行し直してください。")
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
