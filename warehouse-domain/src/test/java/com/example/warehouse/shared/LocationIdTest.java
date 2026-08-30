package com.example.warehouse.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ロケーションも SKU と同じ制約（非空・分離子を含まない）。 */
class LocationIdTest {

    @Test
    void 空のロケーションは作れない() {
        assertThatThrownBy(() -> new LocationId("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 分離子を含むロケーションは作れない() {
        assertThatThrownBy(() -> new LocationId("A@01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 値を保持する() {
        assertThat(new LocationId("A-01").value()).isEqualTo("A-01");
    }
}
