package com.example.warehouse.inventory.command;

import com.example.warehouse.inventory.AllocationId;
import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.shared.Quantity;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 引き当てる。起点は引当ポリシー（P2）（docs/tactical-design.md 集約①在庫）。
 *
 * <p>コアの不変条件「引当可能 = 手持在庫 − 引当済 ≥ 0」を守る受付ゲートは、このコマンドにある。
 */
public record AllocateStock(@TargetAggregateIdentifier InventoryItemId inventoryItemId,
                            AllocationId allocationId, Quantity quantity) {
}
