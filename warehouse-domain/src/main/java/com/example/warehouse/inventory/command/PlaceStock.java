package com.example.warehouse.inventory.command;

import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.ReceiptId;
import com.example.warehouse.shared.Quantity;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 在庫を計上する。起点は格納伝播ポリシー（P1）。
 *
 * <p>{@code putAwayTotal} は<b>判断に使わない</b>（受付ゲートに現れない）。
 * 在庫元帳ビューが二重計上を検出するために下流へ運ぶだけ（docs/decisions.md H42）。
 */
public record PlaceStock(@TargetAggregateIdentifier InventoryItemId inventoryItemId, Quantity quantity,
                         ReceiptId receiptId, Quantity putAwayTotal) {
}
