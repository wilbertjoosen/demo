package com.example.inventory.saga;
import com.example.inventory.service.InventoryService;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class InventoryProvisioningListener {

    private final InventoryService inventoryService;

    @KafkaListener(topics = Topics.PRODUCT_EVENTS, groupId = "inventory-service")
    public void onProductEvent(DomainEvent event) {
        if (EventTypes.PRODUCT_CREATED.equals(event.eventType())) {
            String productId = (String) ((Map<?, ?>) event.payload()).get("productId");
            inventoryService.provisionForNewProduct(productId);
        }
    }
}
