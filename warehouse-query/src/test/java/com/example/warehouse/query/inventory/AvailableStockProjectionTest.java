package com.example.warehouse.query.inventory;

import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.ReceiptId;
import com.example.warehouse.inventory.StockPlaced;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 引当可能在庫ビューの投影テスト（.claude/rules/testing.md：イベント入力 → リードモデル状態）。
 *
 * <p>リポジトリはメモリ上の {@code Map} で代替する。JPA の配線ではなく<b>投影の振る舞い</b>を検証したいため。
 */
class AvailableStockProjectionTest {

    private static final InventoryItemId A01 =
            new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01"));
    private static final ReceiptId RCP = new ReceiptId("RCP-1");

    private Map<String, AvailableStockEntry> rows;
    private AvailableStockRepository repository;
    private AvailableStockProjection projection;

    @BeforeEach
    void setUp() {
        rows = new HashMap<>();
        repository = mock(AvailableStockRepository.class);
        when(repository.findById(anyString()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.<String>getArgument(0))));
        when(repository.save(any(AvailableStockEntry.class))).thenAnswer(call -> {
            AvailableStockEntry entry = call.getArgument(0);
            rows.put(entry.getInventoryItemId(), entry);
            return entry;
        });
        when(repository.findBySkuId(anyString())).thenAnswer(call -> rows.values().stream()
                .filter(row -> row.getSkuId().equals(call.<String>getArgument(0)))
                .toList());
        projection = new AvailableStockProjection(repository);
    }

    @Test
    void 計上されると引当可能在庫の行が生まれる() {
        projection.on(new StockPlaced(A01, new Quantity(50), RCP, new Quantity(50)), at(1));

        AvailableStockEntry entry = repository.findById(A01.asString()).orElseThrow();
        assertThat(entry.getSkuId()).isEqualTo("SKU-A");
        assertThat(entry.getLocationId()).isEqualTo("A-01");
        assertThat(entry.getOnHand()).isEqualTo(50);
        assertThat(entry.getAllocated()).isZero();
        assertThat(entry.getAvailable()).isEqualTo(50);
        assertThat(entry.isFrozen()).isFalse();
    }

    @Test
    void 同じ棚への計上は積み上がる() {
        projection.on(new StockPlaced(A01, new Quantity(30), RCP, new Quantity(30)), at(1));
        projection.on(new StockPlaced(A01, new Quantity(20), RCP, new Quantity(50)), at(2));

        assertThat(repository.findById(A01.asString()).orElseThrow().getOnHand()).isEqualTo(50);
    }

    @Test
    void 同一イベントの二重適用で壊れない() {
        StockPlaced event = new StockPlaced(A01, new Quantity(50), RCP, new Quantity(50));

        projection.on(event, at(1));
        projection.on(event, at(1));

        assertThat(repository.findById(A01.asString()).orElseThrow().getOnHand()).isEqualTo(50);
    }

    @Test
    void 棚ごとに別の行が立つ() {
        projection.on(new StockPlaced(A01, new Quantity(30), RCP, new Quantity(30)), at(1));
        projection.on(new StockPlaced(
                new InventoryItemId(new Sku("SKU-A"), new LocationId("A-02")),
                new Quantity(20), RCP, new Quantity(50)), at(2));

        assertThat(repository.findBySkuId("SKU-A")).hasSize(2);
    }

    private static TrackingToken at(long position) {
        return new GlobalSequenceTrackingToken(position);
    }
}
