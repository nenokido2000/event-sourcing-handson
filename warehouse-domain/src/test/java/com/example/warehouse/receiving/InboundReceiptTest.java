package com.example.warehouse.receiving;

import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 入荷集約のテスト骨子（docs/tactical-design.md 集約②入荷）。
 * 「受け入れた量より多くを棚に上げない」が守る約束。
 */
class InboundReceiptTest {

    private static final ReceiptId RCP = new ReceiptId("RCP-1");
    private static final Sku SKU = new Sku("SKU-A");
    private static final LocationId A01 = new LocationId("A-01");

    private FixtureConfiguration<InboundReceipt> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(InboundReceipt.class);
    }

    @Test
    void 入荷すると入荷が生まれる() {
        fixture.givenNoPriorActivity()
                .when(new ReceiveStock(RCP, SKU, new Quantity(50)))
                .expectEvents(new StockReceived(RCP, SKU, new Quantity(50)));
    }

    @Test
    void 受入量ゼロの入荷は拒否される() {
        fixture.givenNoPriorActivity()
                .when(new ReceiveStock(RCP, SKU, Quantity.ZERO))
                .expectException(InvalidQuantityException.class)
                .expectNoEvents();
    }

    @Test
    void 一部を格納すると格納累計が積み上がる() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new PutAwayStock(RCP, A01, new Quantity(30)))
                .expectEvents(new StockPutAway(RCP, SKU, A01, new Quantity(30), new Quantity(30)));
    }

    @Test
    void 全量を格納すると完了としてクローズされる() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new PutAwayStock(RCP, A01, new Quantity(50)))
                .expectEvents(
                        new StockPutAway(RCP, SKU, A01, new Quantity(50), new Quantity(50)),
                        new InboundReceiptClosed(RCP, Quantity.ZERO, ClosureReason.COMPLETED));
    }

    @Test
    void 複数のロケーションへ分割して格納できる() {
        fixture.given(
                        new StockReceived(RCP, SKU, new Quantity(50)),
                        new StockPutAway(RCP, SKU, A01, new Quantity(30), new Quantity(30)))
                .when(new PutAwayStock(RCP, new LocationId("A-02"), new Quantity(20)))
                .expectEvents(
                        new StockPutAway(RCP, SKU, new LocationId("A-02"), new Quantity(20), new Quantity(50)),
                        new InboundReceiptClosed(RCP, Quantity.ZERO, ClosureReason.COMPLETED));
    }

    @Test
    void 残格納量を超える格納は拒否される() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new PutAwayStock(RCP, A01, new Quantity(51)))
                .expectException(PutAwayExceedsRemainingException.class)
                .expectNoEvents();
    }

    @Test
    void 数量ゼロの格納は拒否される() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new PutAwayStock(RCP, A01, Quantity.ZERO))
                .expectException(InvalidQuantityException.class)
                .expectNoEvents();
    }

    @Test
    void クローズ済みの入荷への格納は拒否される() {
        fixture.given(
                        new StockReceived(RCP, SKU, new Quantity(50)),
                        new StockPutAway(RCP, SKU, A01, new Quantity(30), new Quantity(30)),
                        new InboundReceiptClosed(RCP, new Quantity(20), ClosureReason.DAMAGED))
                .when(new PutAwayStock(RCP, A01, new Quantity(20)))
                .expectException(ReceiptAlreadyClosedException.class)
                .expectNoEvents();
    }

    @Test
    void 破損で打ち切ると残格納量と理由が記録される() {
        fixture.given(
                        new StockReceived(RCP, SKU, new Quantity(50)),
                        new StockPutAway(RCP, SKU, A01, new Quantity(30), new Quantity(30)))
                .when(new CloseInboundReceipt(RCP, ClosureReason.DAMAGED))
                .expectEvents(new InboundReceiptClosed(RCP, new Quantity(20), ClosureReason.DAMAGED));
    }

    @Test
    void 欠品で打ち切っても残格納量が記録される() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new CloseInboundReceipt(RCP, ClosureReason.SHORTAGE))
                .expectEvents(new InboundReceiptClosed(RCP, new Quantity(50), ClosureReason.SHORTAGE));
    }

    @Test
    void クローズ済みの入荷の二重打ち切りは拒否される() {
        fixture.given(
                        new StockReceived(RCP, SKU, new Quantity(50)),
                        new InboundReceiptClosed(RCP, new Quantity(50), ClosureReason.SHORTAGE))
                .when(new CloseInboundReceipt(RCP, ClosureReason.DAMAGED))
                .expectException(ReceiptAlreadyClosedException.class)
                .expectNoEvents();
    }

    @Test
    void COMPLETEDを指定した打ち切り要求は拒否される() {
        fixture.given(new StockReceived(RCP, SKU, new Quantity(50)))
                .when(new CloseInboundReceipt(RCP, ClosureReason.COMPLETED))
                .expectException(InvalidClosureReasonException.class)
                .expectNoEvents();
    }
}
