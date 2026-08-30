package com.example.warehouse.query.inventory;

/**
 * 引当可能在庫を引く。{@code location} が null なら SKU 横断で引く
 * （引当（P2）の引当先選定はこの形を使う / docs/tactical-design.md）。
 */
public record FindAvailableStock(String sku, String location) {
}
