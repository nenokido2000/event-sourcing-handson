package com.example.warehouse.receiving.command;

import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** 入荷する。起点は外部トリガ（調達）。1入荷 = 1SKU。 */
public record ReceiveStock(@TargetAggregateIdentifier ReceiptId receiptId, Sku sku, Quantity quantity) {
}
