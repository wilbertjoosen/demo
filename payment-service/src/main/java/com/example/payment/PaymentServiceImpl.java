package com.example.payment;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class PaymentServiceImpl implements PaymentService {

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
