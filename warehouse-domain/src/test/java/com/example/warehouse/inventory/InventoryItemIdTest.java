package com.example.warehouse.inventory;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Sku;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 複合の在庫IDは文字列化して集約識別子にする（docs/tactical-design.md 集約識別子の表現）。 */
class InventoryItemIdTest {

    @Test
    void SKUとロケーションを分離子でつないだ文字列になる() {
        InventoryItemId id = new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01"));
        assertThat(id.asString()).isEqualTo("SKU-A@A-01");
    }

    @Test
    void 文字列から復元できる() {
        assertThat(InventoryItemId.parse("SKU-A@A-01"))
                .isEqualTo(new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01")));
    }
}
