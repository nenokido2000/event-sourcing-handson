package com.example.warehouse.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数量は非負に閉じる（tactical-design.md 共有カーネル）。 */
class QuantityTest {

    @Test
    void 負の数量は作れない() {
        assertThatThrownBy(() -> new Quantity(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 加算できる() {
        assertThat(new Quantity(30).plus(new Quantity(20))).isEqualTo(new Quantity(50));
    }

    @Test
    void 減算できる() {
        assertThat(new Quantity(50).minus(new Quantity(20))).isEqualTo(new Quantity(30));
    }

    @Test
    void 負になる減算は拒否される() {
        assertThatThrownBy(() -> new Quantity(20).minus(new Quantity(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 以上の比較ができる() {
        assertThat(new Quantity(50).isAtLeast(new Quantity(50))).isTrue();
        assertThat(new Quantity(49).isAtLeast(new Quantity(50))).isFalse();
    }

    @Test
    void ゼロを判定できる() {
        assertThat(Quantity.ZERO.isZero()).isTrue();
        assertThat(new Quantity(1).isZero()).isFalse();
    }
}
