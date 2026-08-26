package com.example.warehouse.atdd;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 受入ステップが HTTP を叩くための道具。ブラウザは使わず Playwright の APIRequestContext だけを使う。
 *
 * <p><b>ここに業務名のメソッドを生やさない</b>（{@code receiveStock(...)} のような）。生やすと API の契約
 * ——URL と本文——が docs/decisions.md H38 とこのクラスの2か所に住むことになり、片方が必ず腐る。
 * このクラスは輸送層に徹し、契約はステップ実装に直接書く。
 */
final class AcceptanceHttpClient {

    private static Playwright playwright;
    private static APIRequestContext request;

    private AcceptanceHttpClient() {
    }

    static void open() {
        playwright = Playwright.create();
        request = playwright.request().newContext(
                new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
    }

    static void close() {
        if (request != null) {
            request.dispose();
            request = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    /**
     * コマンドを送る（POST）。拒否されたら ProblemDetail の本文をそのまま載せて落とす——
     * H38 で例外名を {@code code} に出す契約にした以上、本文が見えないと失敗の原因が分からない。
     */
    static JsonElement command(String path, Map<String, Object> body) {
        APIResponse response = request.post(path, RequestOptions.create().setData(body));
        if (!response.ok()) {
            throw new AssertionError(
                    "POST " + path + " が " + response.status() + " で拒否されました: " + response.text());
        }
        return parse(response);
    }

    /** リードモデルを引く（GET）。行が無いこともあるので、空配列も正常な応答として返す。 */
    static JsonArray query(String path, Map<String, String> queryParams) {
        RequestOptions options = RequestOptions.create();
        queryParams.forEach(options::setQueryParam);

        APIResponse response = request.get(path, options);
        if (!response.ok()) {
            throw new AssertionError(
                    "GET " + path + " が " + response.status() + " で失敗しました: " + response.text());
        }
        return parse(response).getAsJsonArray();
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

    private static JsonElement parse(APIResponse response) {
        String text = response.text();
        return text.isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(text);
    }

    private static String baseUrl() {
        String configured = System.getenv("WAREHOUSE_BASE_URL");
        return configured == null || configured.isBlank() ? "http://localhost:8080" : configured;
    }
}
