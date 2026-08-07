package com.example.delivery;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DeliveryServiceImpl implements DeliveryService {

    /** Demo-only trigger: quantity 11 simulates a failed last-mile delivery. */
    private static final int SIMULATED_FAILURE_QUANTITY = 11;

    private final DeliveryRepository deliveryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void deliver(String orderId, String email, int quantity) {
        boolean success = quantity != SIMULATED_FAILURE_QUANTITY;
        deliveryRepository.save(new Delivery(orderId, success ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED));

        kafkaTemplate.send(Topics.DELIVERY_EVENTS, DomainEvent.of(
                success ? EventTypes.DELIVERED : EventTypes.DELIVERY_FAILED,
                orderId,
                Map.of("email", email == null ? "" : email, "quantity", quantity)));
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
