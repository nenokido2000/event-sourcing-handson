package com.example.warehouse.query.inventory;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 引当可能在庫ビューのクエリハンドラ。
 *
 * <p><b>クエリはイベントを発行しない</b>（CQS / .claude/rules/cqrs-projection.md）。
 * ここに判断は置かず、ビューをそのまま返す。
 */
@Component
public class AvailableStockQueryHandler {

    private final AvailableStockRepository repository;

    public AvailableStockQueryHandler(AvailableStockRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    @Transactional(readOnly = true)
    public List<AvailableStock> handle(FindAvailableStock query) {
        List<AvailableStockEntry> rows = query.location() == null
                ? repository.findBySkuId(query.sku())
                : repository.findBySkuIdAndLocationId(query.sku(), query.location());
        return rows.stream().map(AvailableStock::from).toList();
    }
}
