package com.example.warehouse.inventory;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Sku;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

/**
 * 在庫ID。{@code (Sku, LocationId)} の複合だが、Axon の集約識別子は単一の値を要求するので
 * <b>複合をこの値オブジェクトに閉じ込め、文字列化したものを集約識別子とする</b>
 * （docs/tactical-design.md 集約識別子の表現）。
 *
 * <p>複合識別子を持つのは在庫 BC だけ。他の BC は素材（{@code Sku} / {@code LocationId}）のまま持ち、
 * <b>組み立てるのはポリシー</b>（docs/decisions.md H43）。
 *
 * <p><b>イベントには平坦化せずネストのまま書き出す</b>（docs/decisions.md H52）。
 * {@code "SKU-A@A-01"} の1文字列に畳むと分離子の規約がイベントに焼き込まれ、アップキャスタが永久に
 * それを知る必要が出るため。ただし{@code @JsonValue} を使わない以上、単一値の値オブジェクトのような
 * 「出力は1つ」という保証は効かない——{@code isXxx()} / {@code getXxx()} を足せば導出値が混入する。
 * そこで<b>書き出す項目を {@code @JsonIncludeProperties} で名指しして固定する</b>。
 */
@JsonIncludeProperties({"sku", "locationId"})
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
