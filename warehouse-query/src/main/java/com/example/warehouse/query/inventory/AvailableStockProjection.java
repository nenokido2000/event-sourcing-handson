package com.example.warehouse.query.inventory;

import com.example.warehouse.inventory.event.StockPlaced;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.eventhandling.TrackingToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalLong;

/**
 * 引当可能在庫ビューの投影。
 *
 * <p><b>ビジネスルールを置かない。</b>表示・検索の都合に最適化した非正規化ビューに徹する
 * （.claude/rules/cqrs-projection.md）。
 *
 * <p>処理グループを {@code projection} にするのは、再構築してよい側だと構造で示すため（docs/decisions.md H30）。
 */
@Component
@ProcessingGroup("projection")
public class AvailableStockProjection {

    private final AvailableStockRepository repository;

    public AvailableStockProjection(AvailableStockRepository repository) {
        this.repository = repository;
    }

    /**
     * 在庫が計上された。<b>その棚マスの行が無ければ作る</b>——在庫集約が
     * 「初めて物が入った瞬間に生まれる」のと同じで、ビューにも棚マスタは無い。
     */
    @EventHandler
    @Transactional
    public void on(StockPlaced event, TrackingToken token) {
        String inventoryItemId = event.inventoryItemId().asString();
        AvailableStockEntry entry = repository.findById(inventoryItemId)
                .orElseGet(() -> new AvailableStockEntry(
                        inventoryItemId,
                        event.inventoryItemId().sku().value(),
                        event.inventoryItemId().locationId().value()));

        OptionalLong position = positionOf(token);
        if (entry.alreadyApplied(position)) {
            return;
        }
        entry.placeStock(event.quantity().value(), position);
        repository.save(entry);
    }

    /** 適用位置。同一イベントの二重適用を弾くためだけに使う（docs/decisions.md H28 の集計系ビューの担保）。 */
    private static OptionalLong positionOf(TrackingToken token) {
        return token == null ? OptionalLong.empty() : token.position();
    }
}
