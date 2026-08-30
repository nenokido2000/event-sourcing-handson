package com.example.warehouse.receiving;

import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;

/** 入荷した。 */
public record StockReceived(ReceiptId receiptId, Sku sku, Quantity quantity) {
}
