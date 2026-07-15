#!/bin/bash
# SubagentStop: レビュー/テストのサブエージェント完了時に通知音を鳴らす。
INPUT=$(cat)
AGENT_TYPE=$(echo "$INPUT" | jq -r '.agent_type // "unknown"')
if [ -z "$AGENT_TYPE" ] || [ "$AGENT_TYPE" = "unknown" ]; then
  exit 0
fi
command -v afplay >/dev/null 2>&1 && afplay /System/Library/Sounds/Glass.aiff 2>/dev/null &
exit 0
