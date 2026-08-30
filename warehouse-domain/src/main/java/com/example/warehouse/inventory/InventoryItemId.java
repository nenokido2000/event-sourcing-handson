package com.example.warehouse.inventory;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Sku;

/**
 * 在庫ID。{@code (Sku, LocationId)} の複合だが、Axon の集約識別子は単一の値を要求するので
 * <b>複合をこの値オブジェクトに閉じ込め、文字列化したものを集約識別子とする</b>
 * （docs/tactical-design.md 集約識別子の表現）。
 *
 * <p>複合識別子を持つのは在庫 BC だけ。他の BC は素材（{@code Sku} / {@code LocationId}）のまま持ち、
 * <b>組み立てるのはポリシー</b>（docs/decisions.md H43）。
 */
public record InventoryItemId(Sku sku, LocationId locationId) {

    private static final String SEPARATOR = "@";

    public String asString() {
        return sku.value() + SEPARATOR + locationId.value();
    }

    /**
     * <b>Axon はこの値を集約識別子の文字列表現として使う</b>（{@code @TargetAggregateIdentifier} の
     * 解決とイベントストアのストリームIDの両方）。レコード既定の {@code toString()} のままだと
     * {@code InventoryItemId[sku=...]} になり、{@link #asString()} と食い違う。
     */
    @Override
    public String toString() {
        return asString();
    }

    /** 分離子は {@code Sku} / {@code LocationId} が含まないことを保証しているので、素直に割れる。 */
    public static InventoryItemId parse(String value) {
        int at = value.indexOf(SEPARATOR);
        if (at < 0) {
            throw new IllegalArgumentException("在庫IDは \"SKU@ロケーション\" の形でなければならない: " + value);
        }
        return new InventoryItemId(
                new Sku(value.substring(0, at)),
                new LocationId(value.substring(at + SEPARATOR.length())));
    }
}
