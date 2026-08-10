package com.example.reporting;

import java.time.Instant;

public interface ReportsService {

    OrdersRevenueReport ordersRevenue(Instant from, Instant to);

    SagaHealthReport sagaHealth(Instant from, Instant to);

    TopProductsReport topProducts(Instant from, Instant to, int limit);

    UserGrowthReport userGrowth(Instant from, Instant to);
}
