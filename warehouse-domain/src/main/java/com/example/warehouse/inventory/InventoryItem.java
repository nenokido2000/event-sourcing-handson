package com.example.warehouse.inventory;

import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.Quantity;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.AggregateCreationPolicy;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * 在庫集約（★コア）。SKU × ロケーションの残高と「引当可能 = 手持在庫 − 引当済 ≥ 0」という約束
 * （docs/tactical-design.md 集約①在庫）。
 *
 * <p>このスライスで実装するのは計上（{@link PlaceStock}）だけ。引当・払出・調整・凍結は後続で足す。
 *
 * <p>{@code @Aggregate} を付けないのは入荷集約と同じ理由（このモジュールは Spring に依存しない）。
 */
public class InventoryItem {

    @AggregateIdentifier
    private InventoryItemId id;

    private Quantity onHand = Quantity.ZERO;

    protected InventoryItem() {
        // Axon がイベントリプレイのために使う
    }

    /**
     * 集約の誕生（1つ目の経路）。<b>その棚マスに初めて物が入った瞬間に生まれる</b>ので
     * {@code CREATE_IF_MISSING} にする。棚マスタ登録という概念をドメインに増やさないための形。
     *
     * <p>入荷が「外から与えられたIDで1回だけ生まれる」のと対照的で、
     * <b>識別子の出所が違えば誕生の作り方も違う</b>。
     */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(PlaceStock command) {
        if (command.quantity().isZero()) {
            throw new InvalidQuantityException(
                    "数量ゼロの計上は受け付けない: " + command.inventoryItemId().asString());
        }
        apply(new StockPlaced(command.inventoryItemId(), command.quantity(),
                command.receiptId(), command.putAwayTotal()));
    }

    @EventSourcingHandler
    void on(StockPlaced event) {
        this.id = event.inventoryItemId();
        this.onHand = onHand.plus(event.quantity());
    }
}
