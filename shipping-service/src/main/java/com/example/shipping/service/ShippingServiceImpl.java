package com.example.shipping.service;
import com.example.shipping.enums.ShipmentStatus;
import com.example.shipping.enums.TrackingStatus;
import com.example.shipping.model.Shipment;
import com.example.shipping.repository.ShipmentRepository;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingServiceImpl implements ShippingService {

    /** Demo-only trigger: quantity 7 simulates a warehouse/carrier failure. */
    private static final int SIMULATED_FAILURE_QUANTITY = 7;

    /** Ordered progression the scheduled job walks a shipment through, one stage per tick. */
    private static final List<TrackingStatus> PROGRESSION = List.of(
            TrackingStatus.LABEL_CREATED, TrackingStatus.PICKED_UP, TrackingStatus.IN_TRANSIT,
            TrackingStatus.OUT_FOR_DELIVERY, TrackingStatus.DELIVERED);

    private final ShipmentRepository shipmentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ShippingRateCalculator rateCalculator;

    @Override
    public void ship(
            String orderId, String keycloakUserId, String email, int quantity, ShippingCarrier carrier) {
        BigDecimal cost = rateCalculator.quote(carrier, quantity);

        // Simulated stockout — a system-detected failure, not something a warehouse pick review
        // would catch, so it fails immediately without needing PENDING_WAREHOUSE review.
        if (quantity == SIMULATED_FAILURE_QUANTITY) {
            shipmentRepository.save(new Shipment(orderId, keycloakUserId, ShipmentStatus.FAILED, carrier, cost));
            kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(EventTypes.SHIPPING_FAILED, orderId,
                    Map.of("email", email == null ? "" : email, "quantity", quantity)));
            return;
        }

        Shipment shipment = shipmentRepository.save(new Shipment(
                orderId, keycloakUserId, email, quantity, ShipmentStatus.PENDING_WAREHOUSE, carrier, cost));
        log.info("Shipment {} for order {} is PENDING_WAREHOUSE — awaiting pick confirmation", shipment.getId(), orderId);
    }

    private Shipment findPendingWarehouse(String id) {
        Shipment shipment = shipmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (shipment.getStatus() != ShipmentStatus.PENDING_WAREHOUSE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Shipment " + id + " is not pending warehouse review (status: " + shipment.getStatus() + ")");
        }
        return shipment;
    }

    @Override
    public void confirmPicked(String id) {
        Shipment shipment = findPendingWarehouse(id);
        shipment.setStatus(ShipmentStatus.DISPATCHED);
        shipment.advanceTracking(TrackingStatus.LABEL_CREATED);
        shipmentRepository.save(shipment);
        log.info("Shipment {} for order {} confirmed picked — DISPATCHED", shipment.getId(), shipment.getOrderId());
        kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(EventTypes.SHIPPED, shipment.getOrderId(),
                Map.of("email", shipment.getEmail() == null ? "" : shipment.getEmail(), "quantity", shipment.getQuantity())));
    }

    @Override
    public void reportIssue(String id, String reason) {
        Shipment shipment = findPendingWarehouse(id);
        shipment.setStatus(ShipmentStatus.FAILED);
        shipment.setIssueReason(reason == null || reason.isBlank() ? "reported_by_warehouse" : reason);
        shipmentRepository.save(shipment);
        log.info("Shipment {} for order {} reported as an issue: {}", shipment.getId(), shipment.getOrderId(), shipment.getIssueReason());
        kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(EventTypes.SHIPPING_FAILED, shipment.getOrderId(),
                Map.of("email", shipment.getEmail() == null ? "" : shipment.getEmail(), "quantity", shipment.getQuantity())));
    }

    @Override
    public BigDecimal quote(ShippingCarrier carrier, int quantity) {
        return rateCalculator.quote(carrier, quantity);
    }

    @Override
    public Shipment getByOrderId(String orderId) {
        return shipmentRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    public List<Shipment> list() {
        return shipmentRepository.findByDeletedFalse();
    }

    @Override
    public void delete(String id) {
        Shipment shipment = shipmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        shipment.markDeleted();
        shipmentRepository.save(shipment);
    }

    /**
     * Advances every in-flight, successfully-dispatched shipment one tracking stage per tick —
     * simulates a carrier's own tracking feed without needing a real UPS/DHL integration.
     * 20s pace keeps a full LABEL_CREATED-to-DELIVERED demo run under two minutes.
     */
    @Scheduled(fixedDelay = 20_000)
    void advanceTracking() {
        List<Shipment> inFlight = shipmentRepository.findByStatusAndTrackingStatusNot(ShipmentStatus.DISPATCHED, TrackingStatus.DELIVERED);
        for (Shipment shipment : inFlight) {
            // Pre-existing shipments from before tracking was added have no trackingStatus yet — List.of()'s
            // indexOf(null) throws NPE rather than returning -1, so null needs its own guard.
            if (shipment.getTrackingStatus() == null) {
                continue;
            }
            int nextIndex = PROGRESSION.indexOf(shipment.getTrackingStatus()) + 1;
            if (nextIndex <= 0 || nextIndex >= PROGRESSION.size()) {
                continue;
            }
            shipment.advanceTracking(PROGRESSION.get(nextIndex));
            shipmentRepository.save(shipment);
            log.info("Shipment {} (order {}) advanced to {}", shipment.getId(), shipment.getOrderId(), shipment.getTrackingStatus());
        }
    }
}
