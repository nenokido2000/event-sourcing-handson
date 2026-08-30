package com.example.warehouse.shared;

/**
 * 複合識別子の分離子。在庫ID {@code SKU@ロケーション}・引当ID {@code 注文明細@ロケーション} が使う
 * （docs/tactical-design.md 集約識別子の表現）。
 *
 * <p>1か所に置くのは、<b>分離子を含まないことの検証</b>（{@link Sku} / {@link LocationId}）と
 * <b>組み立て・分解</b>が同じ文字を見ていることを構造で保証するため。
 */
final class Separator {

    static final String COMPOUND = "@";

    private Separator() {
    }
}
