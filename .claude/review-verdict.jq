# es-domain-reviewer のレビュー結果を transcript から取り出す。
#
# 結果の届き方が2通りあるので両方見る:
#   (a) 前景実行 … Agent の tool_result 本文に REVIEW_VERDICT がそのまま入る
#   (b) 非同期実行 … tool_result は起動通知だけで、本文には `agentId: <id>` が入る。
#       実際の結果は後続のタスク通知／TaskOutput の結果として届くので、
#       **その agentId を含み、かつ REVIEW_VERDICT を含むエントリ**を探す。
#
# agentId で結びつけるのが要点。単に "REVIEW_VERDICT" を含む行を拾うと、
# このファイル自体を `cat` した Bash の出力にも当たってしまう。
#
# 出力: {verdict: "PASS"|"FAIL"|null, verdictAt: <結果が届いた時刻>}
# 「レビュー後に Java を触っていないか」の判定は verdictAt と実ファイルの更新時刻で行う
# （Edit/Write を数える方式だと、Bash 経由の書き換えを取りこぼす）。

def verdict_of($text; $at):
  if ($text | test("REVIEW_VERDICT: FAIL")) then {verdict: "FAIL", verdictAt: $at}
  elif ($text | test("REVIEW_VERDICT: PASS")) then {verdict: "PASS", verdictAt: $at}
  else null end;

[inputs]
| reduce .[] as $e (
    {reviewId: null, agentId: null, verdict: null, verdictAt: null};
    . as $acc
    | ($e.message.content) as $raw
    | (if ($raw | type) == "array" then $raw else [] end) as $content
    | if $e.type == "assistant" then
        # レビューを起動し直したら、それ以前の結果は無効にする
        reduce $content[] as $c ($acc;
          if $c.type == "tool_use" and $c.name == "Agent"
             and $c.input.subagent_type == "es-domain-reviewer" then
            {reviewId: $c.id, agentId: null, verdict: null, verdictAt: null}
          else . end)
      elif $acc.reviewId != null then
        # (a) レビュー自身の tool_result
        (reduce $content[] as $c (.;
           if $c.type == "tool_result" and $c.tool_use_id == $acc.reviewId then
             ((if ($c.content | type) == "array"
               then [$c.content[]? | (.text? // empty)] | join("\n")
               else ($c.content // "") end)) as $t
             | (if ($t | test("agentId: [0-9A-Za-z]+"))
                then .agentId = ($t | capture("agentId: (?<id>[0-9A-Za-z]+)").id)
                else . end)
             | (verdict_of($t; $e.timestamp) as $v | if $v then . + $v else . end)
           else . end))
        # (b) 非同期の結果（タスク通知 / TaskOutput）。agentId で本人のものだけ拾う
        | . as $sofar
        | if $sofar.agentId == null then $sofar
          else
            (($e | tostring) as $whole
             | if ($whole | test($sofar.agentId))
               then (verdict_of($whole; $e.timestamp) as $v | if $v then $sofar + $v else $sofar end)
               else $sofar end)
          end
      else $acc end
  )
| {verdict, verdictAt}
