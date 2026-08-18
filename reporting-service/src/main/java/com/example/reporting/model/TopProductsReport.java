package com.example.reporting.model;

import java.math.BigDecimal;
import java.util.List;

public record TopProductsReport(List<ProductStat> products) {

    public record ProductStat(
            String productId,
            String name,
            String sku,
            boolean active,
            long totalQuantityOrdered,
            long orderCount,
            BigDecimal revenue) {
    }
}
