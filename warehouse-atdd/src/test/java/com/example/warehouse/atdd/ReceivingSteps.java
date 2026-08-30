package com.example.warehouse.atdd;

import com.thoughtworks.gauge.Step;

import static com.example.warehouse.atdd.AcceptanceHttpClient.body;
import static com.example.warehouse.atdd.AcceptanceHttpClient.command;
import static com.example.warehouse.atdd.ScenarioIds.of;

/**
 * 入荷（{@code InboundReceipt}）へのコマンド。仕様の正は docs/tactical-design.md の集約②入荷、
 * URL と本文の正は docs/decisions.md H38。
 *
 * <p>識別子は {@link ScenarioIds} を通してシナリオごとに一意にする（H51）。数量は写さない。
 */
public class ReceivingSteps {

    @Step("入荷 <receiptId> で SKU <sku> を <quantity> 受け入れる")
    public void receiveStock(String receiptId, String sku, int quantity) {
        command("/api/receipts", body(
                "receiptId", of(receiptId),
                "sku", of(sku),
                "quantity", quantity));
    }

    @Step("入荷 <receiptId> から ロケーション <locationId> へ <quantity> 格納する")
    public void putAwayStock(String receiptId, String locationId, int quantity) {
        command("/api/receipts/" + of(receiptId) + "/putaways", body(
                "locationId", of(locationId),
                "quantity", quantity));
    }
}
