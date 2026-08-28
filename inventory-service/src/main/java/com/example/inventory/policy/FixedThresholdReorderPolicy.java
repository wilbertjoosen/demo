package com.example.inventory.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default {@link ReorderPolicy}: flag any line whose quantity drops below one fixed, configured threshold. */
@Component
public class FixedThresholdReorderPolicy implements ReorderPolicy {

    private final int threshold;

    public FixedThresholdReorderPolicy(@Value("${inventory.reorder.threshold:10}") int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isLowStock(int quantity) {
        return quantity < threshold;
    }

    @Override
    public int threshold() {
        return threshold;
    }
}
