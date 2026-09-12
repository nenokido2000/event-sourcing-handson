package com.example.warehouse.receiving.command;

import com.example.warehouse.receiving.ClosureReason;
import com.example.warehouse.receiving.ReceiptId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** 入荷を打ち切る。起点は倉庫作業者（破損・欠品）。冪等にしない（docs/decisions.md H14）。 */
public record CloseInboundReceipt(@TargetAggregateIdentifier ReceiptId receiptId, ClosureReason reason) {
}
