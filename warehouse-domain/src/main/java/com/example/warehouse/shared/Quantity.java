package com.example.warehouse.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 数量。非負に閉じる（docs/tactical-design.md 共有カーネル）。
 *
 * <p><b>引当可能（{@code available}）はこの型で表さない。</b>棚卸調整だけが負を持ち込むため
 * （docs/decisions.md H12）、集約内では {@code int} の導出計算にする。
 *
 * <p>イベントには裸の数値として書き出す（{@code @JsonValue} / docs/decisions.md H52）。
 * これが無いと {@link #isZero()} が Jackson にゲッター扱いされ、{@code {"value":50,"zero":false}} と
 * <b>導出値がイベントに焼き付く</b>。形は {@code EventSerializationTest} が固定している。
 */
public record Quantity(@JsonValue int value) {

    public static final Quantity ZERO = new Quantity(0);

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
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
