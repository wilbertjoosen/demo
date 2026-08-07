package com.example.payment;

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
class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentGatewayClient paymentGatewayClient;

    @Override
    public void charge(String orderId, String email, int quantity) {
        // quantity 13 propagates through to the gateway simulator, which declines it — the
        // compensation path (and, here, the circuit breaker) stays testable via the API.
        boolean success = paymentGatewayClient.charge(orderId, quantity);
        Payment payment = paymentRepository.save(new Payment(orderId, email, success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED));

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(
                success ? EventTypes.PAYMENT_COMPLETED : EventTypes.PAYMENT_FAILED,
                orderId,
                Map.of("paymentId", payment.getId(), "email", email == null ? "" : email, "quantity", quantity)));
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
}
