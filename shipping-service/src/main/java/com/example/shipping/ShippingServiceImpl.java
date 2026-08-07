package com.example.shipping;

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
class ShippingServiceImpl implements ShippingService {

    /** Demo-only trigger: quantity 7 simulates a warehouse/carrier failure. */
    private static final int SIMULATED_FAILURE_QUANTITY = 7;

    private final ShipmentRepository shipmentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void ship(String orderId, String email, int quantity) {
        boolean success = quantity != SIMULATED_FAILURE_QUANTITY;
        shipmentRepository.save(new Shipment(orderId, success ? ShipmentStatus.DISPATCHED : ShipmentStatus.FAILED));

        kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(
                success ? EventTypes.SHIPPED : EventTypes.SHIPPING_FAILED,
                orderId,
                Map.of("email", email == null ? "" : email, "quantity", quantity)));
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
}
