package com.example.warehouse.query.inventory;

/**
 * 引当可能在庫の1行（クエリの応答）。
 *
 * <p>JPA エンティティを外へ出さず、応答用の型に詰め替える。ビューの内部表現（{@code lastEventPosition} 等）を
 * API の契約に漏らさないため。
 */
public record AvailableStock(String sku, String location, int onHand, int allocated, int available,
                             boolean frozen, String frozenByStocktakeId) {

    static AvailableStock from(AvailableStockEntry entry) {
        return new AvailableStock(
                entry.getSkuId(), entry.getLocationId(),
                entry.getOnHand(), entry.getAllocated(), entry.getAvailable(),
                entry.isFrozen(), entry.getFrozenByStocktakeId());
    }
}
