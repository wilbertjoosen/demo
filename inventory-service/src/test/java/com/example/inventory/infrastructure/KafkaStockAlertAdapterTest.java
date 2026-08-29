package com.example.inventory.infrastructure;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.inventory.model.InventoryItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaStockAlertAdapterTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    MeterRegistry meterRegistry;
    @Mock
    Counter counter;
    @Captor
    ArgumentCaptor<DomainEvent> eventCaptor;

    KafkaStockAlertAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaStockAlertAdapter(kafkaTemplate, meterRegistry);
    }

    @Test
    void publishLowStock_sendsEventSatisfyingContractAndIncrementsCounter() {
        when(meterRegistry.counter("inventory.lowstock.events")).thenReturn(counter);
        InventoryItem item = new InventoryItem("p1", "MAIN", 3);

        adapter.publishLowStock(item, 10);

        verify(kafkaTemplate).send(eq(Topics.INVENTORY_EVENTS), eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(EventTypes.LOW_STOCK_DETECTED);
        assertThat(event.orderId()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.payload();
        assertThat(payload).containsEntry("productId", "p1").containsEntry("warehouseId", "MAIN")
                .containsEntry("quantity", 3).containsEntry("threshold", 10);
        assertThat(EventContracts.missingFields(EventTypes.LOW_STOCK_DETECTED, payload)).isEmpty();
        verify(counter).increment();
    }
}
