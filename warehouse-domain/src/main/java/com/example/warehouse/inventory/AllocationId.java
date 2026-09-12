package com.example.warehouse.inventory;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.OrderLineId;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

/**
 * 引当ID。{@code (OrderLineId, LocationId)} の複合（docs/tactical-design.md 識別子表）。
 *
 * <p><b>採番しない。引当（P2）が決定的に導出する</b>（docs/decisions.md H26）——
 * 引当先の選定が決定的なので、同じ入力からは必ず同じIDが出る。これが二重発火への守りになっていて、
 * 2回目の要求も同じIDを指すので在庫集約が「同じ引当IDで同じ数量」として黙って弾ける。
 *
 * <p>在庫と出荷の両方が明細キーに使う（H43）。在庫集約の識別子ではないので
 * {@link InventoryItemId} と違い {@code toString()} は上書きしない——Axon が読む場所が無い。
 *
 * <p>イベントにはネストのまま書き出し、項目を名指しで固定する（docs/decisions.md H52）。
 */
@JsonIncludeProperties({"orderLineId", "locationId"})
public record AllocationId(OrderLineId orderLineId, LocationId locationId) {

    private static final String SEPARATOR = "@";

    public AllocationId {
        if (orderLineId == null || locationId == null) {
            throw new IllegalArgumentException("引当IDは注文明細IDとロケーションの両方が要る");
        }
    }

    /** 受入 Spec と REST がこの表現で引当を指す（例: {@code OL-1@A-01}）。 */
    public String asString() {
        return orderLineId.value() + SEPARATOR + locationId.value();
    }

    /** 分離子は {@code OrderLineId} / {@code LocationId} が含まないことを保証しているので、素直に割れる。 */
    public static AllocationId parse(String value) {
        int at = value.indexOf(SEPARATOR);
        if (at < 0) {
            throw new IllegalArgumentException("引当IDは \"注文明細@ロケーション\" の形でなければならない: " + value);
        }
        return new AllocationId(
                new OrderLineId(value.substring(0, at)),
                new LocationId(value.substring(at + SEPARATOR.length())));
    }
}
