package com.example.warehouse.inventory;

import com.example.warehouse.inventory.command.PlaceStock;
import com.example.warehouse.inventory.event.StockPlaced;
import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 在庫集約のテスト骨子（docs/tactical-design.md 集約①在庫）。
 * このスライスでは計上（{@code PlaceStock}）だけを扱う。
 */
class InventoryItemTest {

    private static final InventoryItemId ITEM =
            new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01"));
    private static final ReceiptId RCP = new ReceiptId("RCP-1");

    private FixtureConfiguration<InventoryItem> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(InventoryItem.class);
    }

    @Test
    void 初めて物が入った棚マスで在庫が生まれる() {
        fixture.givenNoPriorActivity()
                .when(new PlaceStock(ITEM, new Quantity(50), RCP, new Quantity(50)))
                .expectEvents(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)));
    }

    @Test
    void 既にある棚マスへの計上は手持在庫を積み増す() {
        fixture.given(new StockPlaced(ITEM, new Quantity(30), RCP, new Quantity(30)))
                .when(new PlaceStock(ITEM, new Quantity(20), RCP, new Quantity(50)))
                .expectEvents(new StockPlaced(ITEM, new Quantity(20), RCP, new Quantity(50)));
    }

    @Test
    void 数量ゼロの計上は拒否される() {
        fixture.givenNoPriorActivity()
                .when(new PlaceStock(ITEM, Quantity.ZERO, RCP, Quantity.ZERO))
                .expectException(InvalidQuantityException.class)
                .expectNoEvents();
    }
}
