package com.example.warehouse.shared;

/**
 * ロケーション（棚1マス）。非空。{@link Sku} と同じ理由で分離子 {@code @} を含まない。
 */
public record LocationId(String value) {

    public LocationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ロケーションは非空でなければならない");
        }
        if (value.contains(Separator.COMPOUND)) {
            throw new IllegalArgumentException(
                    "ロケーションに " + Separator.COMPOUND + " は使えない（在庫IDの分離子）: " + value);
        }
    }
}
