package com.example.warehouse.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 入荷ID（在庫 BC 側の同名別型）。在庫は計上の出所を記録するためだけに持つ。
 *
 * <p>入荷 BC の {@code receiving.ReceiptId} とは<b>別の型</b>。同名の型が2つの BC にあるのは正常で、
 * 区別はパッケージが与える（docs/decisions.md H43）。写像するのは格納伝播ポリシー（P1）。
 *
 * <p>イベントには裸の文字列として書き出す（docs/decisions.md H52）。入荷 BC 側と同じ表現なので、
 * 型が違っても永続化された形は一致する。
 */
public record ReceiptId(@JsonValue String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ReceiptId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("入荷IDは非空でなければならない");
        }
    }

    /** 在庫側は集約識別子ではないが、入荷 BC と同じ文字列表現に揃えておく。 */
    @Override
    public String toString() {
        return value;
    }
}
