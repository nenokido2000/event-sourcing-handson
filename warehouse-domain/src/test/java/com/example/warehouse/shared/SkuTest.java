package com.example.warehouse.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SKU は非空。加えて分離子 {@code @} を含めない——在庫ID {@code SKU@ロケーション} の
 * 分解が壊れるため、値オブジェクトの生成時に保証する（tactical-design.md 集約識別子の表現）。
 */
class SkuTest {

    @Test
    void 空のSKUは作れない() {
        assertThatThrownBy(() -> new Sku(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 分離子を含むSKUは作れない() {
        assertThatThrownBy(() -> new Sku("SKU@A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 値を保持する() {
        assertThat(new Sku("SKU-A").value()).isEqualTo("SKU-A");
    }
}
