package com.example.warehouse.inventory;

import com.example.warehouse.inventory.command.AllocateStock;
import com.example.warehouse.inventory.command.PlaceStock;
import com.example.warehouse.inventory.event.StockAllocated;
import com.example.warehouse.inventory.event.StockPlaced;
import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.Quantity;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * 在庫集約（★コア）。SKU × ロケーションの残高と「引当可能 = 手持在庫 − 引当済 ≥ 0」という約束
 * （docs/tactical-design.md 集約①在庫）。
 *
 * <p>このスライスで実装するのは計上（{@link PlaceStock}）と引当（{@link AllocateStock}）。
 * 払出・調整・凍結は後続で足す。
 *
 * <p>{@code @Aggregate} を付けないのは入荷集約と同じ理由（このモジュールは Spring に依存しない）。
 */
public class InventoryItem {

    @AggregateIdentifier
    private InventoryItemId id;

    private Quantity onHand = Quantity.ZERO;

    /**
     * 引当明細。合計だけでなく<b>明細で持つ</b>——解除・払出が「どの引当か」を指せないと、
     * 二重解除や引当量を超える払出を集約が拒否できない（docs/tactical-design.md 集約①在庫）。
     *
     * <p>払出を足すまでは引当量だけを覚える。払出累計は {@code IssueStock} のスライスで足す。
     */
    private final Map<AllocationId, Quantity> allocations = new HashMap<>();

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

    /**
     * 引き当てる。<b>本PoCのコアの不変条件を強制している場所</b>——
     * 引当可能（= 手持在庫 − 引当済）が要求量に足りなければ例外を投げ、イベントを発行しない。
     *
     * <p>凍結中でも通す（docs/decisions.md H11）。引当は物理を動かさないので実地値を狂わせない。
     */
    @CommandHandler
    public void handle(AllocateStock command) {
        if (command.quantity().isZero()) {
            throw new InvalidQuantityException(
                    "数量ゼロの引当は受け付けない: " + command.allocationId().asString());
        }

        Quantity existing = allocations.get(command.allocationId());
        if (existing != null) {
            // 条件付き冪等（H26）。同じ結果になる要求は黙って受け入れ、矛盾する要求だけ拒否する
            if (existing.equals(command.quantity())) {
                return;
            }
            throw new DuplicateAllocationException(
                    "既に引当済みで数量が違う: " + command.allocationId().asString()
                            + "（既存 " + existing.value() + " / 要求 " + command.quantity().value() + "）");
        }

        if (available() < command.quantity().value()) {
            throw new InsufficientAvailableStockException(
                    "引当可能を超える引当は受け付けない: " + id.asString()
                            + "（引当可能 " + available() + " / 要求 " + command.quantity().value() + "）");
        }

        apply(new StockAllocated(command.inventoryItemId(), command.allocationId(), command.quantity()));
    }

    /**
     * 引当可能。<b>{@code Quantity} で表さない</b>——棚卸調整だけが負を持ち込むため（H12）、
     * 非負に閉じた値オブジェクトでは表せない。導出値なのでフィールドにも持たない。
     */
    private int available() {
        return onHand.value() - allocated();
    }

    private int allocated() {
        return allocations.values().stream().mapToInt(Quantity::value).sum();
    }

    @EventSourcingHandler
    void on(StockPlaced event) {
        this.id = event.inventoryItemId();
        this.onHand = onHand.plus(event.quantity());
    }

    @EventSourcingHandler
    void on(StockAllocated event) {
        this.allocations.put(event.allocationId(), event.quantity());
    }
}
