package com.example.warehouse.receiving;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 入荷ID。伝票番号ではなく<b>ドックに置かれた物のかたまりの識別子</b>（docs/tactical-design.md 集約②）。
 * 外から与えられ、1回だけ集約を生む。
 *
 * <p>イベントには裸の文字列として書き出す（docs/decisions.md H52）。集約識別子の文字列表現
 * （{@link #toString()}）と同じ形になるので、イベントストアの {@code aggregateIdentifier} 列と
 * ペイロードの中身が食い違わない。
 */
public record ReceiptId(@JsonValue String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ReceiptId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("入荷IDは非空でなければならない");
        }
    }

    /** Axon が集約識別子の文字列表現として使うので、レコード既定の toString は使わない。 */
    @Override
    public String toString() {
        return value;
    }
}
