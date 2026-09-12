package com.example.warehouse.command.receiving;

import com.example.warehouse.command.ProcessingGroups;
import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.command.PlaceStock;
import com.example.warehouse.receiving.event.StockPutAway;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 格納伝播ポリシー（P1）。{@code StockPutAway}（入荷）→ {@code PlaceStock}（在庫）。
 * 1トランザクション1集約のため、入荷と在庫のあいだは<b>結果整合</b>になる（docs/decisions.md H6）。
 *
 * <p><b>状態を持たない。</b>イベントごとに独立していて順序を問わない。この点だけが棚卸凍結サーガ（P5）との違い。
 *
 * <p><b>他集約もリードモデルも引かない。</b>在庫IDの組み立てに要る {@code sku} はイベントに載っている
 * （docs/tactical-design.md 集約②の {@code StockPutAway}）。
 * <b>複合識別子を組み立てるのはポリシーの責務</b>——入荷 BC は素材のままイベントに載せる（H43）。
 */
@Component
@ProcessingGroup(ProcessingGroups.POLICY)
public class PutawayPolicy {

    private final CommandGateway commandGateway;

    public PutawayPolicy(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * <b>{@code sendAndWait} で同期に待つ。</b>投げっぱなしにするとコマンドの失敗を捕まえられず、
     * 失敗したイベントが消化されて（トークンが進んで）片落ちに気づけない。
     */
    @EventHandler
    public void on(StockPutAway event) {
        commandGateway.sendAndWait(new PlaceStock(
                new InventoryItemId(event.sku(), event.locationId()),
                event.quantity(),
                // 入荷IDは BC をまたぐので同名別型へ写す（H43）
                new com.example.warehouse.inventory.ReceiptId(event.receiptId().value()),
                event.putAwayTotal()));
    }
}
