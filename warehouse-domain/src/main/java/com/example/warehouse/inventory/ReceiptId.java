package com.example.warehouse.inventory;

/**
 * 入荷ID（在庫 BC 側の同名別型）。在庫は計上の出所を記録するためだけに持つ。
 *
 * <p>入荷 BC の {@code receiving.ReceiptId} とは<b>別の型</b>。同名の型が2つの BC にあるのは正常で、
 * 区別はパッケージが与える（docs/decisions.md H43）。写像するのは格納伝播ポリシー（P1）。
 */
public record ReceiptId(String value) {

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
