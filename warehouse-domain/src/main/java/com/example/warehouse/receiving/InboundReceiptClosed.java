package com.example.warehouse.receiving;

import com.example.warehouse.shared.Quantity;

/**
 * 入荷が終わった。残格納量と理由を<b>記録して</b>終わる——破損・欠品という現実を隠さない
 * （docs/decisions.md H14）。
 */
public record InboundReceiptClosed(ReceiptId receiptId, Quantity remainingQuantity, ClosureReason reason) {
}
