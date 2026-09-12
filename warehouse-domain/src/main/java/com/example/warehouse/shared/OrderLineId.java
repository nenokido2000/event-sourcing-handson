package com.example.warehouse.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 注文明細ID。<b>受注 BC（外部）が振る</b>——倉庫は採番しない（docs/tactical-design.md 共有カーネル）。
 *
 * <p>引当ID は {@code 注文明細@ロケーション} の複合なので、{@link Sku} と同じ理由で
 * <b>分離子 {@code @} を含まないことをここで保証する</b>。
 *
 * <p>イベントには裸の文字列として書き出す（docs/decisions.md H52）。
 */
public record OrderLineId(@JsonValue String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public OrderLineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("注文明細IDは非空でなければならない");
        }
        if (value.contains(Separator.COMPOUND)) {
            throw new IllegalArgumentException(
                    "注文明細IDに " + Separator.COMPOUND + " は使えない（引当IDの分離子）: " + value);
        }
    }
}
