package com.example.warehouse.inventory;

/**
 * 既知の引当IDに対して<b>数量の違う</b>要求が来たので拒否した（docs/decisions.md H26）。
 *
 * <p>同じ数量の再送は拒否ではなく<b>黙って無視</b>する。「同じ結果になる要求は受け入れ、
 * 矛盾する要求は拒否する」という条件付き冪等の、拒否側がこれ。
 */
public class DuplicateAllocationException extends RuntimeException {

    public DuplicateAllocationException(String message) {
        super(message);
    }
}
