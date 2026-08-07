package com.example.shipping;

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
class ShippingServiceImpl implements ShippingService {

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
    public void ship(String orderId, String keycloakUserId, String email, int quantity, ShippingCarrier carrier) {
        boolean success = quantity != SIMULATED_FAILURE_QUANTITY;
        BigDecimal cost = rateCalculator.quote(carrier, quantity);
        shipmentRepository.save(new Shipment(orderId, keycloakUserId, success ? ShipmentStatus.DISPATCHED : ShipmentStatus.FAILED, carrier, cost));

        kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(
                success ? EventTypes.SHIPPED : EventTypes.SHIPPING_FAILED,
                orderId,
                Map.of("email", email == null ? "" : email, "quantity", quantity)));
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
