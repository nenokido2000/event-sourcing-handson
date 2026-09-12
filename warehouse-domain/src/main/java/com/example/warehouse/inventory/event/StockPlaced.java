package com.example.warehouse.inventory.event;

import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.ReceiptId;
import com.example.warehouse.shared.Quantity;

/** 在庫を計上した。手持在庫が増える。 */
public record StockPlaced(InventoryItemId inventoryItemId, Quantity quantity,
                          ReceiptId receiptId, Quantity putAwayTotal) {
}
