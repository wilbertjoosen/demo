package com.example.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OrdersRevenueReport(
        long totalOrders,
        BigDecimal totalRevenue,
        Map<String, Long> byStatus,
        List<DailyCount> dailyOrderCounts) {
}
