package com.example.warehouse.shared;

/**
 * 複合識別子の分離子。在庫ID {@code SKU@ロケーション}・引当ID {@code 注文明細@ロケーション} が使う
 * （docs/tactical-design.md 集約識別子の表現）。
 *
 * <p><b>この定数を見ているのは検証側だけ</b>——分離子を含まないことを確かめる
 * {@link Sku} / {@link LocationId} / {@link OrderLineId} の3つ。
 *
 * <p><b>組み立て・分解側は見ていない。</b>複合識別子（{@code inventory.InventoryItemId} /
 * {@code inventory.AllocationId}）は BC 側にあり、このクラスは package-private なので参照できない。
 * 各自が同じ {@code "@"} を定数で持っている。<b>つまり両者が同じ文字を見ていることは、
 * 構造では保証されていない</b>——揃っていることは、複合IDの往復テスト
 * （{@code InventoryItemIdTest} / {@code AllocationIdTest}）と、分離子を含む素材を弾く
 * 値オブジェクトのテストが、両側から挟んで守っている。
 *
 * <p>公開して1か所に寄せる案は、共有カーネル（docs/decisions.md H39）を分離子という
 * 実装の都合で厚くすることになるため採っていない。
 */
final class Separator {

    static final String COMPOUND = "@";

    private Separator() {
    }
}
