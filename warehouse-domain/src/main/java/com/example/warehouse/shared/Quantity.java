package com.example.warehouse.shared;

/**
 * 数量。非負に閉じる（docs/tactical-design.md 共有カーネル）。
 *
 * <p><b>引当可能（{@code available}）はこの型で表さない。</b>棚卸調整だけが負を持ち込むため
 * （docs/decisions.md H12）、集約内では {@code int} の導出計算にする。
 */
public record Quantity(int value) {

    public static final Quantity ZERO = new Quantity(0);

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("数量は非負でなければならない: " + value);
        }
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value);
    }

    /** 負になるなら例外。減算できるかは呼び出し側が {@link #isAtLeast} で確かめる。 */
    public Quantity minus(Quantity other) {
        return new Quantity(value - other.value);
    }

    public boolean isAtLeast(Quantity other) {
        return value >= other.value;
    }

    public boolean isZero() {
        return value == 0;
    }
}
