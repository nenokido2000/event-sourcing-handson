package com.example.warehouse.query.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 引当可能在庫ビューの読み書き。索引は {@code (sku_id, available)} と {@code (location_id)}（H29）。 */
public interface AvailableStockRepository extends JpaRepository<AvailableStockEntry, String> {

    List<AvailableStockEntry> findBySkuIdAndLocationId(String skuId, String locationId);

    List<AvailableStockEntry> findBySkuId(String skuId);
}
