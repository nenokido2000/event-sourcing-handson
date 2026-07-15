[inputs] | to_entries
| reduce .[] as $e (
    {lastEdit: -1, reviewId: null, reviewLine: -1, verdict: null};
    . as $acc
    | ($e.value.message.content) as $raw
    | (if ($raw | type) == "array" then $raw else [] end) as $content
    | if $e.value.type == "assistant" then
        reduce $content[] as $c ($acc;
          if $c.type == "tool_use" and ($c.name == "Edit" or $c.name == "Write")
             and (($c.input.file_path // "") | test("\\.java$")) then
            .lastEdit = $e.key
          elif $c.type == "tool_use" and $c.name == "Agent"
             and $c.input.subagent_type == "es-domain-reviewer" then
            .reviewId = $c.id | .reviewLine = $e.key | .verdict = null
          else . end
        )
      elif $e.value.type == "user" and $acc.reviewId != null then
        reduce $content[] as $c ($acc;
          if $c.type == "tool_result" and $c.tool_use_id == $acc.reviewId then
            (if ($c.content | type) == "array"
             then [$c.content[]? | (.text? // empty)] | join("\n")
             else ($c.content // "") end) as $t
            | .verdict = (
                if ($t | test("REVIEW_VERDICT: FAIL")) then "FAIL"
                elif ($t | test("REVIEW_VERDICT: PASS")) then "PASS"
                else .verdict end
              )
          else . end
        )
      else $acc end
  )
