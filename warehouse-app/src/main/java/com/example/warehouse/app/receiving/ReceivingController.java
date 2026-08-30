package com.example.warehouse.app.receiving;

import com.example.warehouse.receiving.CloseInboundReceipt;
import com.example.warehouse.receiving.ClosureReason;
import com.example.warehouse.receiving.PutAwayStock;
import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.receiving.ReceiveStock;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入荷へのコマンド。URL と本文の正は docs/decisions.md H38。
 *
 * <p><b>コマンドは同期</b>（{@code sendAndWait}）。結果整合が要るのは投影の反映だけで、
 * コマンドの受理まで非同期にすると失敗の切り分けが難しくなる（H38）。
 *
 * <p>「出来事の名詞」をサブリソースにして POST する（{@code /putaways} / {@code /closure}）。
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceivingController {

    private final CommandGateway commandGateway;

    public ReceivingController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public record ReceiveStockRequest(String receiptId, String sku, int quantity) {
    }

    public record PutAwayStockRequest(String locationId, int quantity) {
    }

    public record CloseReceiptRequest(String reason) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void receive(@RequestBody ReceiveStockRequest request) {
        commandGateway.sendAndWait(new ReceiveStock(
                new ReceiptId(request.receiptId()),
                new Sku(request.sku()),
                new Quantity(request.quantity())));
    }

    @PostMapping("/{receiptId}/putaways")
    @ResponseStatus(HttpStatus.CREATED)
    public void putAway(@PathVariable String receiptId, @RequestBody PutAwayStockRequest request) {
        commandGateway.sendAndWait(new PutAwayStock(
                new ReceiptId(receiptId),
                new LocationId(request.locationId()),
                new Quantity(request.quantity())));
    }

    @PostMapping("/{receiptId}/closure")
    @ResponseStatus(HttpStatus.CREATED)
    public void close(@PathVariable String receiptId, @RequestBody CloseReceiptRequest request) {
        commandGateway.sendAndWait(new CloseInboundReceipt(
                new ReceiptId(receiptId),
                ClosureReason.valueOf(request.reason())));
    }
}
