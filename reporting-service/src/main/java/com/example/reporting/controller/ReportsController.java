package com.example.reporting.controller;
import com.example.reporting.model.OrderDrillDownItem;
import com.example.reporting.model.OrdersRevenueReport;
import com.example.reporting.model.SagaHealthReport;
import com.example.reporting.model.TopProductsReport;
import com.example.reporting.model.UserDrillDownItem;
import com.example.reporting.model.UserGrowthReport;
import com.example.reporting.service.ReportsService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportsController {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 10;

    private final ReportsService reportsService;

    @GetMapping("/orders")
    public OrdersRevenueReport ordersRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportsService.ordersRevenue(resolveFrom(from), resolveTo(to));
    }

    @GetMapping("/saga-health")
    public SagaHealthReport sagaHealth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportsService.sagaHealth(resolveFrom(from), resolveTo(to));
    }

    @GetMapping("/top-products")
    public TopProductsReport topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit) {
        return reportsService.topProducts(resolveFrom(from), resolveTo(to), limit == null ? DEFAULT_TOP_PRODUCTS_LIMIT : limit);
    }

    @GetMapping("/user-growth")
    public UserGrowthReport userGrowth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportsService.userGrowth(resolveFrom(from), resolveTo(to));
    }

    @GetMapping("/orders/drill-down")
    public List<OrderDrillDownItem> ordersDrillDown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String stage) {
        return reportsService.ordersDrillDown(resolveFrom(from), resolveTo(to), status, date, stage);
    }

    @GetMapping("/users/drill-down")
    public List<UserDrillDownItem> usersDrillDown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportsService.usersDrillDown(resolveFrom(from), resolveTo(to), date);
    }

    private Instant resolveFrom(Instant from) {
        return from != null ? from : Instant.now().minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
    }

    private Instant resolveTo(Instant to) {
        return to != null ? to : Instant.now();
    }
}
