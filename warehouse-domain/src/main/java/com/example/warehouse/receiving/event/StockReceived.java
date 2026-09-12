package com.example.warehouse.receiving.event;

import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;

/** 入荷した。 */
public record StockReceived(ReceiptId receiptId, Sku sku, Quantity quantity) {
}
