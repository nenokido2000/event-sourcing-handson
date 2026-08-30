package com.example.warehouse.receiving;

import com.example.warehouse.shared.InvalidQuantityException;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * 入荷集約。受入ドックに置かれた1SKU の物のかたまりと、それを守る約束
 * （docs/tactical-design.md 集約②入荷）。
 *
 * <p>守る不変条件は <b>格納累計 ≤ 受入量</b>（＝残格納量 ≥ 0）。強制点は {@link PutAwayStock} の受付ゲート。
 *
 * <p>残格納量はフィールドに持たず、受入量と格納累計から導出する——積み上げた事実を保持し、
 * 残りは引き算で出す流儀（在庫の引当済と同じ）。
 *
 * <p><b>{@code @Aggregate} を付けない。</b>あれは Spring のステレオタイプで、このモジュールは
 * Spring に依存しない（CLAUDE.md のモジュール構成 / docs/decisions.md H33）。
 * Axon への登録は {@code warehouse-app} の Axon 設定が行う。
 */
public class InboundReceipt {

    @AggregateIdentifier
    private ReceiptId id;

    private Sku sku;
    private Quantity receivedQty;
    private Quantity putAwayQty;
    private boolean closed;

    protected InboundReceipt() {
        // Axon がイベントリプレイのために使う
    }

    /**
     * 集約の誕生。入荷は {@code ReceiptId} を外から与えられて1回だけ生まれるので、
     * コンストラクタコマンドにする（在庫の {@code CREATE_IF_MISSING} とは対照的）。
     * 同一IDの二重受入はイベントストアの一意性制約が拒否する。
     */
    @CommandHandler
    public InboundReceipt(ReceiveStock command) {
        if (command.quantity().isZero()) {
            throw new InvalidQuantityException("受入量がゼロの入荷は受け付けない: " + command.receiptId().value());
        }
        apply(new StockReceived(command.receiptId(), command.sku(), command.quantity()));
    }

    @CommandHandler
    public void handle(PutAwayStock command) {
        if (command.quantity().isZero()) {
            throw new InvalidQuantityException("数量ゼロの格納は受け付けない: " + id.value());
        }
        if (closed) {
            throw new ReceiptAlreadyClosedException("終わった入荷には格納できない: " + id.value());
        }
        Quantity remaining = remaining();
        if (!remaining.isAtLeast(command.quantity())) {
            throw new PutAwayExceedsRemainingException(
                    "受け入れた以上を棚に上げることはできない: 残格納量 " + remaining.value()
                            + " に対して格納量 " + command.quantity().value());
        }

        Quantity putAwayTotal = putAwayQty.plus(command.quantity());
        apply(new StockPutAway(id, sku, command.locationId(), command.quantity(), putAwayTotal));

        // 残格納量がゼロになったら、続けて完了クローズを原子的に発行する（H14）
        if (receivedQty.equals(putAwayTotal)) {
            apply(new InboundReceiptClosed(id, Quantity.ZERO, ClosureReason.COMPLETED));
        }
    }

    /**
     * 打ち切る。<b>残格納量が残っていても通す</b>——破損・欠品という現実を残量と理由として記録して終わらせる。
     * 冪等にしない（発行元が人間なので、二重打ち切りは誤操作として伝える / H14）。
     */
    @CommandHandler
    public void handle(CloseInboundReceipt command) {
        if (closed) {
            throw new ReceiptAlreadyClosedException("すでに終わっている入荷は打ち切れない: " + id.value());
        }
        if (command.reason() == ClosureReason.COMPLETED) {
            throw new InvalidClosureReasonException(
                    "全量格納による完了は集約が自動で発行する。打ち切りの理由には指定できない");
        }
        apply(new InboundReceiptClosed(id, remaining(), command.reason()));
    }

    @EventSourcingHandler
    void on(StockReceived event) {
        this.id = event.receiptId();
        this.sku = event.sku();
        this.receivedQty = event.quantity();
        this.putAwayQty = Quantity.ZERO;
        this.closed = false;
    }

    @EventSourcingHandler
    void on(StockPutAway event) {
        this.putAwayQty = event.putAwayTotal();
    }

    @EventSourcingHandler
    void on(InboundReceiptClosed event) {
        this.closed = true;
    }

    /** 残格納量。常に ≥ 0（負を持ち込む経路がこの集約には無い）。 */
    private Quantity remaining() {
        return receivedQty.minus(putAwayQty);
    }
}
