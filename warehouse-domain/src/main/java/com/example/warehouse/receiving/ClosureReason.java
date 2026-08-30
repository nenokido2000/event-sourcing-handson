package com.example.warehouse.receiving;

/** 入荷の終わり方（docs/decisions.md H14）。全量格納 / 破損 / 欠品。 */
public enum ClosureReason {
    /** 残格納量ゼロで自動的に閉じた。人が指定することはできない */
    COMPLETED,
    DAMAGED,
    SHORTAGE
}
