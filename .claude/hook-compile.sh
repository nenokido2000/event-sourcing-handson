#!/bin/bash
# PostToolUse(Edit|Write): 変更された *.java をコンパイルする。
# Gradle雛形が未生成の間は no-op（M0で有効化）。
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# .java 以外の編集は対象外
echo "$FILE" | grep -qE '\.java$' || exit 0

# gradlew が無ければまだ足場が無いのでスキップ
[ -x "$ROOT/gradlew" ] || exit 0

cd "$ROOT" && ./gradlew compileJava compileTestJava -q 2>&1
