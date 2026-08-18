package com.example.reporting.service;
import com.example.reporting.config.ReportingTopology;
import com.example.reporting.model.DailyCount;
import com.example.reporting.model.OrderDrillDownItem;
import com.example.reporting.model.OrderMetric;
import com.example.reporting.model.OrdersRevenueReport;
import com.example.reporting.model.ProductRef;
import com.example.reporting.model.SagaHealthReport;
import com.example.reporting.model.TopProductsReport;
import com.example.reporting.model.UserDrillDownItem;
import com.example.reporting.model.UserGrowthReport;
import com.example.reporting.model.UserRegistration;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.streams.KafkaStreamsInteractiveQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every report is a plain Java-streams aggregation over the values currently held in the Kafka
 * Streams state stores (see ReportingTopology) — admin-only, low-frequency, bounded-volume reads
 * don't justify a second aggregation layer (e.g. a Streams "interactive query" that pre-computes
 * windowed rollups) on top of what's already a materialized view.
 */
@Service
@RequiredArgsConstructor
public class ReportsServiceImpl implements ReportsService {

    private static final String USER_CANCELLED_BEFORE_PAYMENT = "USER_CANCELLED_BEFORE_PAYMENT";

    private final KafkaStreamsInteractiveQueryService interactiveQueryService;

    @Override
    public OrdersRevenueReport ordersRevenue(Instant from, Instant to) {
        List<OrderMetric> metrics = orderMetricsBetween(from, to);
        Map<String, BigDecimal> priceByProduct = priceByProduct();

        long totalOrders = metrics.size();
        BigDecimal totalRevenue = metrics.stream()
                .filter(this::isRevenueRecognized)
                .map(m -> revenueOf(m, priceByProduct))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> byStatus = metrics.stream()
                .collect(Collectors.groupingBy(OrderMetric::getStatus, Collectors.counting()));
        List<DailyCount> dailyOrderCounts = dailyCounts(metrics.stream().map(OrderMetric::getOrderCreatedAt));

        return new OrdersRevenueReport(totalOrders, totalRevenue, byStatus, dailyOrderCounts);
    }

    @Override
    public SagaHealthReport sagaHealth(Instant from, Instant to) {
        List<OrderMetric> metrics = orderMetricsBetween(from, to);

        long inProgress = metrics.stream().filter(m -> isInProgress(m.getStatus())).count();
        long completed = metrics.stream().filter(m -> "CONFIRMED".equals(m.getStatus())).count();
        long cancelled = metrics.stream().filter(m -> "CANCELLED".equals(m.getStatus())).count();
        long terminal = completed + cancelled;
        double cancellationRate = terminal == 0 ? 0.0 : (double) cancelled / terminal;

        OptionalDouble avg = metrics.stream()
                .filter(m -> "CONFIRMED".equals(m.getStatus()) && m.getConfirmedAt() != null)
                .mapToLong(m -> Duration.between(m.getOrderCreatedAt(), m.getConfirmedAt()).toMinutes())
                .average();
        Double avgMinutes = avg.isPresent() ? avg.getAsDouble() : null;

        Map<String, Long> failuresByStage = metrics.stream()
                .filter(m -> "CANCELLED".equals(m.getStatus()))
                .collect(Collectors.groupingBy(
                        m -> m.getFailureStage() == null ? USER_CANCELLED_BEFORE_PAYMENT : m.getFailureStage(),
                        Collectors.counting()));

        return new SagaHealthReport(inProgress, completed, cancelled, cancellationRate, avgMinutes, failuresByStage);
    }

    @Override
    public TopProductsReport topProducts(Instant from, Instant to, int limit) {
        List<OrderMetric> metrics = orderMetricsBetween(from, to);
        Map<String, ProductRef> productsById = this.<ProductRef>allValues(ReportingTopology.PRODUCT_REFS_STORE).stream()
                .collect(Collectors.toMap(ProductRef::getProductId, p -> p, (a, b) -> b));

        Map<String, List<OrderMetric>> byProduct = metrics.stream()
                .collect(Collectors.groupingBy(OrderMetric::getProductId));

        List<TopProductsReport.ProductStat> stats = byProduct.entrySet().stream()
                .map(entry -> {
                    String productId = entry.getKey();
                    List<OrderMetric> orders = entry.getValue();
                    ProductRef ref = productsById.get(productId);
                    long totalQuantity = orders.stream().mapToLong(OrderMetric::getQuantity).sum();
                    BigDecimal revenue = orders.stream()
                            .filter(this::isRevenueRecognized)
                            .map(m -> ref == null ? BigDecimal.ZERO : ref.getPrice().multiply(BigDecimal.valueOf(m.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new TopProductsReport.ProductStat(
                            productId,
                            ref == null ? "(deleted product)" : ref.getName(),
                            ref == null ? "-" : ref.getSku(),
                            ref != null && ref.isActive(),
                            totalQuantity,
                            orders.size(),
                            revenue);
                })
                .sorted(Comparator.comparingLong(TopProductsReport.ProductStat::totalQuantityOrdered).reversed())
                .limit(limit)
                .toList();

        return new TopProductsReport(stats);
    }

    @Override
    public UserGrowthReport userGrowth(Instant from, Instant to) {
        List<UserRegistration> registrations = this.<UserRegistration>allValues(ReportingTopology.USER_REGISTRATIONS_STORE).stream()
                .filter(u -> !u.getRegisteredAt().isBefore(from) && !u.getRegisteredAt().isAfter(to))
                .toList();
        List<DailyCount> daily = dailyCounts(registrations.stream().map(UserRegistration::getRegisteredAt));
        return new UserGrowthReport(registrations.size(), daily);
    }

    @Override
    public List<OrderDrillDownItem> ordersDrillDown(Instant from, Instant to, String status, LocalDate date, String failureStage) {
        return orderMetricsBetween(from, to).stream()
                .filter(m -> status == null || status.equals(m.getStatus()))
                .filter(m -> date == null || date.equals(m.getOrderCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                .filter(m -> failureStage == null
                        || failureStage.equals(m.getFailureStage() == null ? USER_CANCELLED_BEFORE_PAYMENT : m.getFailureStage()))
                .sorted(Comparator.comparing(OrderMetric::getOrderCreatedAt).reversed())
                .map(m -> new OrderDrillDownItem(
                        m.getOrderId(), m.getEmail(), m.getProductId(), m.getQuantity(), m.getStatus(),
                        m.getPaymentMethod(), m.getShippingCarrier(), m.getOrderCreatedAt()))
                .toList();
    }

    @Override
    public List<UserDrillDownItem> usersDrillDown(Instant from, Instant to, LocalDate date) {
        return this.<UserRegistration>allValues(ReportingTopology.USER_REGISTRATIONS_STORE).stream()
                .filter(u -> !u.getRegisteredAt().isBefore(from) && !u.getRegisteredAt().isAfter(to))
                .filter(u -> date == null || date.equals(u.getRegisteredAt().atZone(ZoneOffset.UTC).toLocalDate()))
                .sorted(Comparator.comparing(UserRegistration::getRegisteredAt).reversed())
                .map(u -> new UserDrillDownItem(u.getUserId(), u.getUsername(), u.getEmail(), u.getRegisteredAt()))
                .toList();
    }

    private List<OrderMetric> orderMetricsBetween(Instant from, Instant to) {
        return this.<OrderMetric>allValues(ReportingTopology.ORDER_METRICS_STORE).stream()
                .filter(m -> m.getOrderCreatedAt() != null && !m.getOrderCreatedAt().isBefore(from) && !m.getOrderCreatedAt().isAfter(to))
                .toList();
    }

    private Map<String, BigDecimal> priceByProduct() {
        return this.<ProductRef>allValues(ReportingTopology.PRODUCT_REFS_STORE).stream()
                .collect(Collectors.toMap(ProductRef::getProductId, ProductRef::getPrice, (a, b) -> b));
    }

    private <V> List<V> allValues(String storeName) {
        ReadOnlyKeyValueStore<String, V> store =
                interactiveQueryService.retrieveQueryableStore(storeName, QueryableStoreTypes.keyValueStore());
        List<V> values = new ArrayList<>();
        try (KeyValueIterator<String, V> iterator = store.all()) {
            iterator.forEachRemaining((KeyValue<String, V> kv) -> values.add(kv.value));
        }
        return values;
    }

    private boolean isRevenueRecognized(OrderMetric metric) {
        return !"PENDING_PAYMENT".equals(metric.getStatus()) && !"CANCELLED".equals(metric.getStatus());
    }

    private boolean isInProgress(String status) {
        return "PENDING_PAYMENT".equals(status) || "PAID".equals(status) || "SHIPPED".equals(status);
    }

    private BigDecimal revenueOf(OrderMetric metric, Map<String, BigDecimal> priceByProduct) {
        BigDecimal price = priceByProduct.getOrDefault(metric.getProductId(), BigDecimal.ZERO);
        return price.multiply(BigDecimal.valueOf(metric.getQuantity()));
    }

    private List<DailyCount> dailyCounts(Stream<Instant> timestamps) {
        Map<LocalDate, Long> byDate = timestamps
                .collect(Collectors.groupingBy(t -> t.atZone(ZoneOffset.UTC).toLocalDate(), TreeMap::new, Collectors.counting()));
        return byDate.entrySet().stream()
                .map(e -> new DailyCount(e.getKey(), e.getValue()))
                .toList();
    }
}
