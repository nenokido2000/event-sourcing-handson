package com.example.warehouse.atdd;

import com.thoughtworks.gauge.Step;

import static com.example.warehouse.atdd.AcceptanceHttpClient.body;
import static com.example.warehouse.atdd.AcceptanceHttpClient.command;

/**
 * 入荷（{@code InboundReceipt}）へのコマンド。仕様の正は docs/tactical-design.md の集約②入荷、
 * URL と本文の正は docs/decisions.md H38。
 */
public class ReceivingSteps {

    @Step("入荷 <receiptId> で SKU <sku> を <quantity> 受け入れる")
    public void receiveStock(String receiptId, String sku, int quantity) {
        command("/api/receipts", body(
                "receiptId", receiptId,
                "sku", sku,
                "quantity", quantity));
    }

    @Step("入荷 <receiptId> から ロケーション <locationId> へ <quantity> 格納する")
    public void putAwayStock(String receiptId, String locationId, int quantity) {
        command("/api/receipts/" + receiptId + "/putaways", body(
                "locationId", locationId,
                "quantity", quantity));
    }
}
