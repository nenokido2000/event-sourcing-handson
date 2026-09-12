package com.example.warehouse.inventory;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.OrderLineId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 引当IDは注文明細 × ロケーションの複合。<b>採番せず決定的に導出する</b>（docs/decisions.md H26）ので、
 * 同じ入力からは必ず同じIDになる——これが引当（P2）の二重発火への守りになっている。
 */
class AllocationIdTest {

    @Test
    void 注文明細とロケーションを分離子でつないだ文字列になる() {
        AllocationId id = new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01"));
        assertThat(id.asString()).isEqualTo("OL-1@A-01");
    }

    @Test
    void 文字列から復元できる() {
        assertThat(AllocationId.parse("OL-1@A-01"))
                .isEqualTo(new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01")));
    }

    @Test
    void 同じ注文明細と同じロケーションからは同じ引当IDになる() {
        assertThat(new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01")))
                .isEqualTo(new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01")));
    }

    @Test
    void 分離子を含む注文明細IDは作れない() {
        assertThatThrownBy(() -> new OrderLineId("OL@1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
