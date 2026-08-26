package com.example.warehouse.atdd;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.gauge.Step;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.warehouse.atdd.AcceptanceHttpClient.params;
import static com.example.warehouse.atdd.AcceptanceHttpClient.query;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引当可能在庫ビューへの期待。列の定義は docs/tactical-design.md のリードモデル、
 * URL は docs/decisions.md H38（{@code GET /api/available-stock?sku=&location=}）。
 *
 * <p>投影はコマンドの後から追いつくので、期待は必ず {@link EventualConsistency} 越しに書く。
 * 行がまだ無い状態も「不一致」として扱われ、そのまま再試行に乗る。
 */
public class AvailableStockSteps {

    @Step("引当可能在庫ビューの SKU <sku> ロケーション <location> は 手持在庫 <onHand> 引当済 <allocated> 引当可能 <available>")
    public void availableStockIs(String sku, String location, int onHand, int allocated, int available) {
        Map<String, Integer> expected = stock(onHand, allocated, available);

        EventualConsistency.awaitAssertion(
                "SKU " + sku + " / ロケーション " + location + " の引当可能在庫",
                () -> {
                    JsonArray rows = query("/api/available-stock", params("sku", sku, "location", location));

                    assertThat(rows)
                            .as("SKU %s / ロケーション %s の行", sku, location)
                            .hasSize(1);

                    JsonObject row = rows.get(0).getAsJsonObject();
                    assertThat(actualStock(row))
                            .as("SKU %s / ロケーション %s の数量", sku, location)
                            .isEqualTo(expected);
                });
    }

    private static Map<String, Integer> actualStock(JsonObject row) {
        return stock(
                row.get("onHand").getAsInt(),
                row.get("allocated").getAsInt(),
                row.get("available").getAsInt());
    }

    /** 3つまとめて比べる（1つずつ assert すると、最初の不一致で止まって残りが見えない）。 */
    private static Map<String, Integer> stock(int onHand, int allocated, int available) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("手持在庫", onHand);
        stock.put("引当済", allocated);
        stock.put("引当可能", available);
        return stock;
    }
}
