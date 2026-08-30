package com.example.warehouse.shared;

/**
 * SKU（在庫保管単位）。非空。
 *
 * <p>在庫ID は {@code SKU@ロケーション} の複合を文字列化したものなので、
 * <b>分離子 {@code @} を含まないことをここで保証する</b>（docs/tactical-design.md 集約識別子の表現）。
 */
public record Sku(String value) {

    public Sku {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SKU は非空でなければならない");
        }
        if (value.contains(Separator.COMPOUND)) {
            throw new IllegalArgumentException(
                    "SKU に " + Separator.COMPOUND + " は使えない（在庫IDの分離子）: " + value);
        }
    }
}
