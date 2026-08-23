package com.example.reporting.service;

import com.example.reporting.model.OrderDrillDownItem;
import com.example.reporting.model.OrdersRevenueReport;
import com.example.reporting.model.SagaHealthReport;
import com.example.reporting.model.TopProductsReport;
import com.example.reporting.model.UserDrillDownItem;
import com.example.reporting.model.UserGrowthReport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ReportsService {

    OrdersRevenueReport ordersRevenue(Instant from, Instant to);

    SagaHealthReport sagaHealth(Instant from, Instant to);

    TopProductsReport topProducts(Instant from, Instant to, int limit);

    UserGrowthReport userGrowth(Instant from, Instant to);

    List<OrderDrillDownItem> ordersDrillDown(Instant from, Instant to, String status, LocalDate date, String failureStage);

    List<UserDrillDownItem> usersDrillDown(Instant from, Instant to, LocalDate date);
}
