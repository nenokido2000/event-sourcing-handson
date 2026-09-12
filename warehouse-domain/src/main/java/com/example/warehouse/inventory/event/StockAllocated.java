package com.example.warehouse.inventory.event;

import com.example.warehouse.inventory.AllocationId;
import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.shared.Quantity;

/** 引き当てた。手持在庫は動かず、引当済が増える。 */
public record StockAllocated(InventoryItemId inventoryItemId, AllocationId allocationId, Quantity quantity) {
}
