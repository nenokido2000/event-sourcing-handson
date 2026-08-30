/**
 * 在庫 BC（★コアサブドメイン = 在庫引当）。SKU × ロケーション（棚1マス）の残高と、
 * 「引当可能 = 手持在庫 − 引当済 ≥ 0」という約束の責任者。
 *
 * <p>物そのものではなく<b>約束</b>を守るのがこの集約の責務（docs/tactical-design.md 集約①在庫）。
 */
package com.example.warehouse.inventory;
