package com.example.inventory.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedThresholdReorderPolicyTest {

    private final FixedThresholdReorderPolicy policy = new FixedThresholdReorderPolicy(10);

    @Test
    void belowThreshold_isLowStock() {
        assertThat(policy.isLowStock(9)).isTrue();
    }

    @Test
    void atThreshold_isNotLowStock() {
        assertThat(policy.isLowStock(10)).isFalse();
    }

    @Test
    void aboveThreshold_isNotLowStock() {
        assertThat(policy.isLowStock(11)).isFalse();
    }

    @Test
    void threshold_returnsConfiguredValue() {
        assertThat(policy.threshold()).isEqualTo(10);
    }
}
