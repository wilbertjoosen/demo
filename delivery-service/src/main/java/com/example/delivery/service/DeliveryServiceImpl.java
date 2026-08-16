package com.example.delivery.service;
import com.example.delivery.enums.DeliveryStatus;
import com.example.delivery.model.Delivery;
import com.example.delivery.repository.DeliveryRepository;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryServiceImpl implements DeliveryService {

    /** Demo-only trigger: quantity 11 simulates a failed last-mile delivery. */
    private static final int SIMULATED_FAILURE_QUANTITY = 11;

    private final DeliveryRepository deliveryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void deliver(String orderId, String email, int quantity) {
        // Simulated failure — a system-detected condition (e.g. address validation), not
        // something a delivery-agent confirmation would catch, so it fails immediately without
        // needing PENDING_DELIVERY_AGENT review — same rationale as shipping-service's stockout.
        if (quantity == SIMULATED_FAILURE_QUANTITY) {
            deliveryRepository.save(new Delivery(orderId, DeliveryStatus.FAILED));
            kafkaTemplate.send(Topics.DELIVERY_EVENTS, DomainEvent.of(EventTypes.DELIVERY_FAILED, orderId,
                    Map.of("email", email == null ? "" : email, "quantity", quantity)));
            return;
        }

        Delivery delivery = deliveryRepository.save(new Delivery(orderId, DeliveryStatus.PENDING_DELIVERY_AGENT, email, quantity));
        log.info("Delivery {} for order {} is PENDING_DELIVERY_AGENT — awaiting delivery confirmation", delivery.getId(), orderId);
    }

    private Delivery findPendingDeliveryAgent(String id) {
        Delivery delivery = deliveryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (delivery.getStatus() != DeliveryStatus.PENDING_DELIVERY_AGENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Delivery " + id + " is not pending delivery-agent review (status: " + delivery.getStatus() + ")");
        }
        return delivery;
    }

    @Override
    public void confirmDelivered(String id) {
        Delivery delivery = findPendingDeliveryAgent(id);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        deliveryRepository.save(delivery);
        log.info("Delivery {} for order {} confirmed delivered", delivery.getId(), delivery.getOrderId());
        kafkaTemplate.send(Topics.DELIVERY_EVENTS, DomainEvent.of(EventTypes.DELIVERED, delivery.getOrderId(),
                Map.of("email", delivery.getEmail() == null ? "" : delivery.getEmail(), "quantity", delivery.getQuantity())));
    }

    @Override
    public void reportIssue(String id, String reason) {
        Delivery delivery = findPendingDeliveryAgent(id);
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setIssueReason(reason == null || reason.isBlank() ? "reported_by_delivery_agent" : reason);
        deliveryRepository.save(delivery);
        log.info("Delivery {} for order {} reported as an issue: {}", delivery.getId(), delivery.getOrderId(), delivery.getIssueReason());
        kafkaTemplate.send(Topics.DELIVERY_EVENTS, DomainEvent.of(EventTypes.DELIVERY_FAILED, delivery.getOrderId(),
                Map.of("email", delivery.getEmail() == null ? "" : delivery.getEmail(), "quantity", delivery.getQuantity())));
    }

    @Override
    public List<Delivery> list() {
        return deliveryRepository.findByDeletedFalse();
    }

    @Override
    public void delete(String id) {
        Delivery delivery = deliveryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        delivery.markDeleted();
        deliveryRepository.save(delivery);
    }
}
