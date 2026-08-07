package com.example.shipping;

import com.example.common.model.ShippingCarrier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mock carrier rate card — stands in for a real UPS/DHL rating API call, same "in-process
 * simulator" approach {@link PaymentGatewaySimulatorController} uses for the payment gateway.
 * Flat base fee + per-item fee, carrier rates deliberately differ so the choice at checkout
 * is meaningful (DHL costs more per item but has a lower base fee here).
 */
@Component
class ShippingRateCalculator {

    BigDecimal quote(ShippingCarrier carrier, int quantity) {
        BigDecimal base = switch (carrier) {
            case UPS -> BigDecimal.valueOf(9.99);
            case DHL -> BigDecimal.valueOf(7.99);
        };
        BigDecimal perItem = switch (carrier) {
            case UPS -> BigDecimal.valueOf(1.50);
            case DHL -> BigDecimal.valueOf(2.25);
        };
        return base.add(perItem.multiply(BigDecimal.valueOf(quantity))).setScale(2, RoundingMode.HALF_UP);
    }
}
