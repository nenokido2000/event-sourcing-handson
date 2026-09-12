package com.example.warehouse.inventory;

import com.example.warehouse.inventory.command.AllocateStock;
import com.example.warehouse.inventory.command.PlaceStock;
import com.example.warehouse.inventory.event.StockAllocated;
import com.example.warehouse.inventory.event.StockPlaced;
import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.OrderLineId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 在庫集約のテスト骨子（docs/tactical-design.md 集約①在庫）。
 * このスライスでは計上（{@code PlaceStock}）と引当（{@code AllocateStock}）を扱う。
 * 払出・調整・凍結は後続のスライスで足す。
 */
class InventoryItemTest {

    private static final InventoryItemId ITEM =
            new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01"));
    private static final ReceiptId RCP = new ReceiptId("RCP-1");
    private static final AllocationId ALLOC =
            new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01"));
    private static final AllocationId OTHER_ALLOC =
            new AllocationId(new OrderLineId("OL-2"), new LocationId("A-01"));

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

    // --- 引当（AllocateStock）★コア -------------------------------------------------

    @Test
    void 引当可能の範囲なら引き当てられる() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)))
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(30)))
                .expectEvents(new StockAllocated(ITEM, ALLOC, new Quantity(30)));
    }

    @Test
    void 引当可能ちょうどまで引き当てられる() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)))
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(50)))
                .expectEvents(new StockAllocated(ITEM, ALLOC, new Quantity(50)));
    }

    /** コアの不変条件。引当可能を負にする要求は拒否し、イベントを1件も出さない。 */
    @Test
    void 引当可能を超える引当は拒否される() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)))
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(51)))
                .expectException(InsufficientAvailableStockException.class)
                .expectNoEvents();
    }

    /** 引当済は手持在庫を減らさないが、引当可能は減る。2件目はその残りまでしか引けない。 */
    @Test
    void 既存の引当は引当可能を押さえる() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)),
                        new StockAllocated(ITEM, ALLOC, new Quantity(30)))
                .when(new AllocateStock(ITEM, OTHER_ALLOC, new Quantity(21)))
                .expectException(InsufficientAvailableStockException.class)
                .expectNoEvents();
    }

    @Test
    void 数量ゼロの引当は拒否される() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)))
                .when(new AllocateStock(ITEM, ALLOC, Quantity.ZERO))
                .expectException(InvalidQuantityException.class)
                .expectNoEvents();
    }

    /**
     * 条件付き冪等（H26）。引当（P2）は計画を立て直して再送しうるので、
     * <b>同じ結果になる要求は黙って受け入れる</b>——例外も投げず、イベントも出さない。
     */
    @Test
    void 同じ引当IDで同じ数量の再送は黙って無視される() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)),
                        new StockAllocated(ITEM, ALLOC, new Quantity(30)))
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(30)))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    /** 矛盾する要求は拒否する（H26）。再処理が引当ビューから計画を立て直して自己修正する。 */
    @Test
    void 同じ引当IDで数量が違う要求は拒否される() {
        fixture.given(new StockPlaced(ITEM, new Quantity(50), RCP, new Quantity(50)),
                        new StockAllocated(ITEM, ALLOC, new Quantity(30)))
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(40)))
                .expectException(DuplicateAllocationException.class)
                .expectNoEvents();
    }

    /** 在庫が生まれていない棚マスへの引当は、集約が無いので届かない（計上が唯一の誕生経路）。 */
    @Test
    void 在庫の無い棚マスへは引き当てられない() {
        fixture.givenNoPriorActivity()
                .when(new AllocateStock(ITEM, ALLOC, new Quantity(1)))
                .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class)
                .expectNoEvents();
    }
}
