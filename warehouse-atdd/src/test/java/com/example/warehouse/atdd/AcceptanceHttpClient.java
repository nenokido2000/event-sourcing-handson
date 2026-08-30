package com.example.warehouse.atdd;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 受入ステップが HTTP を叩くための道具。JDK 標準の {@link HttpClient} だけを使う（docs/decisions.md H50）。
 *
 * <p><b>ここに業務名のメソッドを生やさない</b>（{@code receiveStock(...)} のような）。生やすと API の契約
 * ——URL と本文——が docs/decisions.md H38 とこのクラスの2か所に住むことになり、片方が必ず腐る。
 * このクラスは輸送層に徹し、契約はステップ実装に直接書く。
 */
final class AcceptanceHttpClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Gson GSON = new Gson();

    private AcceptanceHttpClient() {
    }

    /**
     * コマンドを送る（POST）。<b>拒否されても投げない</b>——Spec が失敗する要求と照合を別の行に書くため、
     * 応答は {@link RejectedCommand} に覚えさせて次のステップに渡す（H50）。
     */
    static JsonElement command(String path, Map<String, Object> body) {
        // 前のコマンドの拒否を誰も照合していないなら、ここで落とす（拒否を黙って上書きしない）
        RejectedCommand.failIfUnconsumed();

        HttpRequest request = HttpRequest.newBuilder(uri(path, Map.of()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request, "POST " + path);
        if (isError(response)) {
            RejectedCommand.remember("POST " + path, response.statusCode(), response.body());
            return JsonNull.INSTANCE;
        }
        return parse(response.body());
    }

    /**
     * リードモデルを引く（GET）。行が無いこともあるので、空配列も正常な応答として返す。
     * <b>こちらは失敗を即座に落とす</b>——照会が 4xx/5xx なのは常に異常で、Spec が期待する場面が無い。
     */
    static JsonArray query(String path, Map<String, String> queryParams) {
        HttpRequest request = HttpRequest.newBuilder(uri(path, queryParams)).GET().build();

        HttpResponse<String> response = send(request, "GET " + path);
        if (isError(response)) {
            throw new AssertionError(
                    "GET " + path + " が " + response.statusCode() + " で失敗しました: " + response.body());
        }
        return parse(response.body()).getAsJsonArray();
    }

    /** 本文・クエリ文字列を組み立てる小道具（呼び出し側の見た目を契約の表に近づけるため）。 */
    static Map<String, Object> body(Object... keyValuePairs) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            body.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return body;
    }

    static Map<String, String> params(String... keyValuePairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            params.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return params;
    }

    private static HttpResponse<String> send(HttpRequest request, String what) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new AssertionError(
                    what + " に失敗しました（アプリは起動していますか: " + baseUrl() + "）", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(what + " が中断されました", interrupted);
        }
    }

    private static boolean isError(HttpResponse<String> response) {
        return response.statusCode() >= 400;
    }

    private static JsonElement parse(String text) {
        return text == null || text.isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(text);
    }

    private static URI uri(String path, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return URI.create(baseUrl() + path);
        }
        StringJoiner query = new StringJoiner("&");
        queryParams.forEach((name, value) -> query.add(encode(name) + "=" + encode(value)));
        return URI.create(baseUrl() + path + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String baseUrl() {
        String configured = System.getenv("WAREHOUSE_BASE_URL");
        return configured == null || configured.isBlank() ? "http://localhost:8080" : configured;
    }
}
