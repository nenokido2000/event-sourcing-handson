package com.example.warehouse.receiving.event;

import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;

/**
 * 棚へ格納した。
 *
 * <p>{@code sku} を載せるのは、格納伝播（P1）が入荷集約を引かずに在庫IDを組み立てられるようにするため。
 * {@code putAwayTotal}（格納累計）を載せるのは、{@code (receiptId, putAwayTotal)} が<b>この格納を一意に指す</b>ので、
 * 在庫元帳ビューが P1 の二重計上を検出できるようにするため（docs/decisions.md H42）。
 */
public record StockPutAway(ReceiptId receiptId, Sku sku, LocationId locationId,
                           Quantity quantity, Quantity putAwayTotal) {
}
