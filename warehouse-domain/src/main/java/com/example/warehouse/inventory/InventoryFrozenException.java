package com.example.warehouse.inventory;

/**
 * 凍結中（棚卸対象）の棚に物理を動かすコマンドが来た。実地値が狂うため拒否する。
 *
 * <p>これは<b>ドメイン上ありうる拒否</b>で、ポリシーはリトライせず棚卸干渉ビューに積む
 * （docs/decisions.md H22）。棚卸スライス（M3-b）で凍結が入るまでは発生しない。
 */
public class InventoryFrozenException extends RuntimeException {

    public InventoryFrozenException(String message) {
        super(message);
    }
}
