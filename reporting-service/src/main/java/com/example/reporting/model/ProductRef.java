package com.example.reporting.model;
import com.example.reporting.config.ReportingTopology;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Local copy of product name/sku/price, folded from product-events by the Kafka Streams KTable
 * aggregator in ReportingTopology — lets the top-products report join without ever calling
 * product-service synchronously.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductRef {

    private String productId;
    private String name;
    private String sku;
    private BigDecimal price = BigDecimal.ZERO;
    private boolean active = true;

    public ProductRef apply(DomainEvent event) {
        Map<?, ?> payload = (Map<?, ?>) event.payload();
        this.productId = (String) payload.get("productId");
        switch (event.eventType()) {
            case EventTypes.PRODUCT_CREATED, EventTypes.PRODUCT_UPDATED -> {
                this.name = (String) payload.get("name");
                this.sku = (String) payload.get("sku");
                this.price = priceOf(payload);
                this.active = true;
            }
            case EventTypes.PRODUCT_DELETED -> this.active = false;
            default -> { }
        }
        return this;
    }

    private BigDecimal priceOf(Map<?, ?> payload) {
        Object price = payload.get("price");
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price instanceof BigDecimal bd ? bd : new BigDecimal(price.toString());
    }
}
