package com.example.warehouse.shared;

/**
 * 数量ゼロのコマンド。在庫・入荷の両スライスが共有する（docs/tactical-design.md 例外一覧）。
 *
 * <p>「負の数量」は {@link Quantity} が生成時に弾くのでここには来ない。
 * ここが受け持つのは<b>ゼロという有効な数量を、意味の無いコマンドとして拒否する</b>ドメインの判断。
 */
public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(String message) {
        super(message);
    }
}
