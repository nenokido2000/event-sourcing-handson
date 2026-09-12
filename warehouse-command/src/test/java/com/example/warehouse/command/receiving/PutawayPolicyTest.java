package com.example.warehouse.command.receiving;

import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.command.PlaceStock;
import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.receiving.event.StockPutAway;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 格納伝播ポリシー（P1）のテスト骨子（docs/tactical-design.md ポリシー P1・P3・P4）。
 * 「イベント入力 → 送られるコマンド」を検証する。
 */
class PutawayPolicyTest {

    @Test
    void 格納したら在庫へ計上を送る() {
        CommandGateway commandGateway = mock(CommandGateway.class);
        PutawayPolicy policy = new PutawayPolicy(commandGateway);

        policy.on(new StockPutAway(
                new ReceiptId("RCP-1"), new Sku("SKU-A"), new LocationId("A-01"),
                new Quantity(30), new Quantity(30)));

        ArgumentCaptor<PlaceStock> sent = ArgumentCaptor.forClass(PlaceStock.class);
        verify(commandGateway).sendAndWait(sent.capture());

        assertThat(sent.getValue()).isEqualTo(new PlaceStock(
                new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01")),
                new Quantity(30),
                new com.example.warehouse.inventory.ReceiptId("RCP-1"),
                new Quantity(30)));
    }
}
