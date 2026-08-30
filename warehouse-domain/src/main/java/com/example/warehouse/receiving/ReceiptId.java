package com.example.warehouse.receiving;

/**
 * 入荷ID。伝票番号ではなく<b>ドックに置かれた物のかたまりの識別子</b>（docs/tactical-design.md 集約②）。
 * 外から与えられ、1回だけ集約を生む。
 */
public record ReceiptId(String value) {

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
