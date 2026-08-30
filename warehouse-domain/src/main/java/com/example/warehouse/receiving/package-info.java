/**
 * 入荷 BC。受入ドックに置かれた1SKU の物のかたまりと、「受け入れた量より多くを棚に上げない」約束。
 *
 * <p>引当には関与しない（ロケーション未確定の在庫は引けない / docs/decisions.md H3・H6）。
 * 検品は独立させず受入〜格納に内包する（H8）。
 */
package com.example.warehouse.receiving;
