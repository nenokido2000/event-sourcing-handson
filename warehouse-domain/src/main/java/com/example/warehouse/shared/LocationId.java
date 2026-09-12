package com.example.warehouse.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ロケーション（棚1マス）。非空。{@link Sku} と同じ理由で分離子 {@code @} を含まない。
 *
 * <p>イベントには裸の文字列として書き出す（docs/decisions.md H52）。
 */
public record LocationId(@JsonValue String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
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
