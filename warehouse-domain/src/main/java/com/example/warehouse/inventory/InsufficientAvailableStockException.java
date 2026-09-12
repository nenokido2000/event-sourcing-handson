package com.example.warehouse.inventory;

/**
 * 引当可能を超える引当を拒否した。<b>本PoCのコアの不変条件を守っている例外</b>
 * （docs/tactical-design.md 集約①在庫の不変条件表）。
 *
 * <p>引当（P2）はこれを握りつぶさず伝播させる。再処理が引当ビューから計画を立て直すことで
 * 自己修正する（docs/decisions.md H26 / H49）。
 */
public class InsufficientAvailableStockException extends RuntimeException {

    public InsufficientAvailableStockException(String message) {
        super(message);
    }
}
