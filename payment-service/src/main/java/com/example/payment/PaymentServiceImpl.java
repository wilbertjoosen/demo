package com.example.payment;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentServiceImpl implements PaymentService {

    /** No instant gateway to charge for these — they resolve via {@link #resolvePendingManualPayments()} instead. */
    private static final Set<PaymentMethod> MANUAL_METHODS = Set.of(PaymentMethod.BANK_TRANSFER, PaymentMethod.CASH);

    /** Stands in for how long a real bank transfer/cash-drop-off confirmation would take to arrive. */
    private static final Duration MOCK_PROCESSING_DELAY = Duration.ofSeconds(30);

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentGatewayAvailability gatewayAvailability;

    @Override
    public void charge(
            String orderId, String keycloakUserId, String email, int quantity, PaymentMethod method, ShippingCarrier shippingCarrier) {
        if (!gatewayAvailability.isAvailable(method)) {
            Payment payment = paymentRepository.save(new Payment(orderId, email, method, PaymentStatus.FAILED, "gateway_unavailable"));
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_FAILED, orderId,
                    Map.of("paymentId", payment.getId(), "email", email == null ? "" : email, "quantity", quantity,
                            "reason", "gateway_unavailable")));
            return;
        }

        if (MANUAL_METHODS.contains(method)) {
            Payment payment = paymentRepository.save(new Payment(orderId, email, method, PaymentStatus.PENDING,
                    quantity, keycloakUserId, shippingCarrier));
            log.info("Payment {} for order {} ({}) is PENDING — resolves after the mock processing delay",
                    payment.getId(), orderId, method);
            return;
        }

        // quantity 13 propagates through to the gateway simulator, which declines it — the
        // compensation path (and, here, the circuit breaker) stays testable via the API.
        boolean success = paymentGatewayClient.charge(orderId, quantity);
        Payment payment = paymentRepository.save(new Payment(orderId, email, method,
                success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED, success ? null : "declined"));

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(
                success ? EventTypes.PAYMENT_COMPLETED : EventTypes.PAYMENT_FAILED,
                orderId,
                Map.of("paymentId", payment.getId(), "email", email == null ? "" : email, "quantity", quantity,
                        "userId", keycloakUserId == null ? "" : keycloakUserId,
                        "shippingCarrier", shippingCarrier.name())));
    }

    /**
     * Resolves BANK_TRANSFER/CASH payments once they've sat PENDING for {@link #MOCK_PROCESSING_DELAY} —
     * always to COMPLETED for now (no decline path for a manual method). A future manual-review step
     * replaces this auto-resolution with an actual finance-role approval action.
     */
    @Scheduled(fixedDelay = 10_000)
    void resolvePendingManualPayments() {
        Instant cutoff = Instant.now().minus(MOCK_PROCESSING_DELAY);
        for (Payment payment : paymentRepository.findByStatus(PaymentStatus.PENDING)) {
            if (payment.getCreatedAt() == null || payment.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            log.info("Payment {} for order {} ({}) resolved to COMPLETED after mock processing delay",
                    payment.getId(), payment.getOrderId(), payment.getMethod());
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_COMPLETED, payment.getOrderId(),
                    Map.of("paymentId", payment.getId(), "email", payment.getEmail() == null ? "" : payment.getEmail(),
                            "quantity", payment.getQuantity(),
                            "userId", payment.getKeycloakUserId() == null ? "" : payment.getKeycloakUserId(),
                            "shippingCarrier", payment.getShippingCarrier().name())));
        }
    }

    @Override
    public void refund(String orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(
                    EventTypes.PAYMENT_REFUNDED, orderId,
                    Map.of("paymentId", payment.getId(), "email", payment.getEmail() == null ? "" : payment.getEmail())));
        });
    }

    @Override
    public List<Payment> list() {
        return paymentRepository.findByDeletedFalse();
    }

    @Override
    public void delete(String id) {
        Payment payment = paymentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        payment.markDeleted();
        paymentRepository.save(payment);
    }

    @Override
    public Map<PaymentMethod, Boolean> availableMethods() {
        return gatewayAvailability.snapshot();
    }

    @Override
    public void setMethodAvailability(PaymentMethod method, boolean available) {
        gatewayAvailability.setAvailable(method, available);
    }
}
