package com.example.warehouse.receiving;

import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** 棚へ格納する。起点は倉庫作業者。ロケーションは格納時に確定する（分割格納を許す）。 */
public record PutAwayStock(@TargetAggregateIdentifier ReceiptId receiptId, LocationId locationId, Quantity quantity) {
}
