package com.example.warehouse.atdd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.thoughtworks.gauge.datastore.ScenarioDataStore;

/**
 * 拒否されたコマンドの応答。Spec は失敗する要求と照合を別の行に書くので、応答を1つだけ覚えて次のステップへ渡す
 * （docs/decisions.md H50）。
 *
 * <p>覚え場所が {@link ScenarioDataStore} なのは、<b>Gauge のランナーがシナリオ境界で自動的に空にする</b>ため。
 * static フィールドで持つとシナリオをまたいで拒否が残り、後続シナリオが誤って緑になる。
 *
 * @param what   何の要求か（失敗時のメッセージ用）
 * @param status HTTP ステータス
 * @param body   応答本文（RFC 9457 ProblemDetail を想定 / docs/decisions.md H38）
 */
record RejectedCommand(String what, int status, String body) {

    private static final String KEY = "直前の拒否";

    static void remember(String what, int status, String body) {
        ScenarioDataStore.put(KEY, new RejectedCommand(what, status, body));
    }

    /**
     * 覚えている拒否を取り出して忘れる。照合ステップ専用。
     *
     * @throws AssertionError 直前の要求が拒否されていないとき
     */
    static RejectedCommand consume() {
        Object remembered = ScenarioDataStore.remove(KEY);
        if (remembered == null) {
            throw new AssertionError(
                    "直前の要求は拒否されていません（受け付けられたか、そもそも要求を送っていません）");
        }
        return (RejectedCommand) remembered;
    }

    /**
     * 拒否が誰にも照合されないまま残っていたら落とす。次のコマンドの手前と、シナリオの終わりで呼ぶ。
     *
     * <p>これが無いと、拒否されたことに Spec が気づかないまま次へ進み、シナリオが緑のまま通る。
     */
    static void failIfUnconsumed() {
        Object pending = ScenarioDataStore.remove(KEY);
        if (pending != null) {
            RejectedCommand rejection = (RejectedCommand) pending;
            throw new AssertionError(
                    rejection.what + " が " + rejection.status + " で拒否されましたが、"
                            + "どのステップも照合していません: " + rejection.body);
        }
    }

    /**
     * ProblemDetail の拡張フィールド {@code code}（例外の単純名 / docs/decisions.md H38）。
     * 契約どおりでない応答は、生の本文を見せて落とす——照合先が無いと何が起きたか分からない。
     */
    String code() {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonSyntaxException notJson) {
            throw new AssertionError(what + " の応答が JSON ではありません: " + body, notJson);
        }
        if (!parsed.isJsonObject()) {
            throw new AssertionError(what + " の応答が JSON オブジェクトではありません: " + body);
        }
        JsonObject problem = parsed.getAsJsonObject();
        if (!problem.has("code")) {
            throw new AssertionError(
                    what + " の応答に ProblemDetail の code がありません（H38 の契約）: " + body);
        }
        return problem.get("code").getAsString();
    }
}
