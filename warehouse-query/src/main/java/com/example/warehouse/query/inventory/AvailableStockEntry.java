package com.example.warehouse.query.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.OptionalLong;

/**
 * 引当可能在庫ビューの1行（docs/tactical-design.md リードモデル）。
 *
 * <p>読み手: 引当（P2）の引当先選定 / 棚卸凍結（P5）の対象列挙 / 棚卸開始の前段バリデーション。
 *
 * <p><b>ドメインの値オブジェクトを持ち込まない</b>（CLAUDE.md のモジュール構成）。値は素のカラムに落ちる。
 * スキーマを {@code readmodel} に明示するのは、Axon のエンティティ側にスキーマを指定できないため
 * 既定を書き側（{@code eventstore}）に寄せているから（docs/decisions.md H36）。
 */
@Entity
@Table(name = "available_stock_view", schema = "readmodel")
public class AvailableStockEntry {

    /** {@code SKU@ロケーション}。ドメインの識別子をそのまま PK に載せる（H29） */
    @Id
    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @Column(name = "sku_id", nullable = false)
    private String skuId;

    @Column(name = "location_id", nullable = false)
    private String locationId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(name = "allocated", nullable = false)
    private int allocated;

    /**
     * <b>符号付き</b>。導出値だが列に持つ——引当先選定の絞り込み・並べ替えに使うため。
     * 棚卸調整が負を持ち込みうる（docs/decisions.md H12）ので非負ではない。
     */
    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "frozen", nullable = false)
    private boolean frozen;

    @Column(name = "frozen_by_stocktake_id")
    private String frozenByStocktakeId;

    /** 集計系ビューの冪等性の担保。この位置以前のイベントは無視する（H28） */
    @Column(name = "last_event_position", nullable = false)
    private long lastEventPosition;

    protected AvailableStockEntry() {
        // JPA 用
    }

    AvailableStockEntry(String inventoryItemId, String skuId, String locationId) {
        this.inventoryItemId = inventoryItemId;
        this.skuId = skuId;
        this.locationId = locationId;
        this.onHand = 0;
        this.allocated = 0;
        this.available = 0;
        this.frozen = false;
        this.lastEventPosition = -1L;
    }

    /**
     * 同一イベントの二重適用で壊れないための門番（cqrs-projection.md の冪等性）。
     *
     * <p>位置が分からないときは弾かない。<b>ここで「不明」を「適用済み」と誤って扱うと、
     * 以降のイベントが黙って落ちる</b>ほうが害が大きい。
     */
    boolean alreadyApplied(OptionalLong eventPosition) {
        return eventPosition.isPresent() && eventPosition.getAsLong() <= lastEventPosition;
    }

    void placeStock(int quantity, OptionalLong eventPosition) {
        this.onHand += quantity;
        this.available = onHand - allocated;
        eventPosition.ifPresent(position -> this.lastEventPosition = position);
    }

    public String getInventoryItemId() {
        return inventoryItemId;
    }

    public String getSkuId() {
        return skuId;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getAllocated() {
        return allocated;
    }

    public int getAvailable() {
        return available;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public String getFrozenByStocktakeId() {
        return frozenByStocktakeId;
    }
}
