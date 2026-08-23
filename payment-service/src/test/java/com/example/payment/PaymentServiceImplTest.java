package com.example.payment;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.model.Payment;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.PaymentGatewayAvailability;
import com.example.payment.service.PaymentGatewayClient;
import com.example.payment.service.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    PaymentGatewayClient paymentGatewayClient;
    @Mock
    PaymentGatewayAvailability gatewayAvailability;

    PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, kafkaTemplate, paymentGatewayClient, gatewayAvailability);
    }

    private Payment withId(Payment payment, String id) {
        try {
            var field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return payment;
    }

    private Payment withCreatedAt(Payment payment, Instant createdAt) {
        try {
            var field = Payment.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(payment, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return payment;
    }

    @Test
    void charge_gatewayUnavailable_savesFailedPaymentAndPublishesPaymentFailed() {
        when(gatewayAvailability.isAvailable(PaymentMethod.CREDIT_CARD)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), "pay-1"));

        paymentService.charge("order-1", "user-1", "a@b.com", 2, PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS);

        verifyNoInteractions(paymentGatewayClient);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_FAILED);
        assertThat(published.orderId()).isEqualTo("order-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("paymentId", "pay-1").containsEntry("reason", "gateway_unavailable");
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_FAILED, payload)).isEmpty();
    }

    @Test
    void charge_gatewayAvailableAndAccepted_savesCompletedPaymentAndPublishesPaymentCompleted() {
        when(gatewayAvailability.isAvailable(PaymentMethod.CREDIT_CARD)).thenReturn(true);
        when(paymentGatewayClient.charge("order-1", 2)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), "pay-2"));

        paymentService.charge("order-1", "user-1", "a@b.com", 2, PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_COMPLETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("paymentId", "pay-2").containsEntry("userId", "user-1")
                .containsEntry("shippingCarrier", "UPS");
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_COMPLETED, payload)).isEmpty();
    }

    @Test
    void charge_gatewayAvailableButDeclined_savesFailedPaymentAndPublishesPaymentFailed() {
        when(gatewayAvailability.isAvailable(PaymentMethod.CREDIT_CARD)).thenReturn(true);
        when(paymentGatewayClient.charge(anyString(), anyInt())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), "pay-3"));

        paymentService.charge("order-1", "user-1", "a@b.com", 13, PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_FAILED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_FAILED, payload)).isEmpty();
    }

    @Test
    void charge_bankTransfer_savesPendingPaymentAndPublishesInstructionsRequired() {
        when(gatewayAvailability.isAvailable(PaymentMethod.BANK_TRANSFER)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), "pay-4"));

        paymentService.charge("order-1", "user-1", "a@b.com", 2, PaymentMethod.BANK_TRANSFER, ShippingCarrier.UPS);

        verifyNoInteractions(paymentGatewayClient);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getKeycloakUserId()).isEqualTo("user-1");
        assertThat(saved.getShippingCarrier()).isEqualTo(ShippingCarrier.UPS);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_INSTRUCTIONS_REQUIRED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("paymentId", "pay-4").containsEntry("method", "BANK_TRANSFER");
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_INSTRUCTIONS_REQUIRED, payload)).isEmpty();
    }

    @Test
    void charge_cash_savesPendingPaymentAndPublishesInstructionsRequired() {
        when(gatewayAvailability.isAvailable(PaymentMethod.CASH)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), "pay-5"));

        paymentService.charge("order-1", "user-1", "a@b.com", 1, PaymentMethod.CASH, ShippingCarrier.DHL);

        verifyNoInteractions(paymentGatewayClient);
        verify(paymentRepository).save(any(Payment.class));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(EventTypes.PAYMENT_INSTRUCTIONS_REQUIRED);
    }

    @Test
    void resolvePendingManualPayments_pastMockDelay_movesToAwaitingReviewWithoutPublishing() {
        Payment pending = withCreatedAt(
                withId(new Payment("order-1", "a@b.com", PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING,
                        3, "user-1", ShippingCarrier.UPS), "pay-6"),
                Instant.now().minus(31, ChronoUnit.SECONDS));
        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending));

        paymentService.resolvePendingManualPayments();

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.AWAITING_REVIEW);
        verify(paymentRepository).save(pending);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void resolvePendingManualPayments_withinMockDelay_leavesPending() {
        Payment recent = withCreatedAt(
                withId(new Payment("order-1", "a@b.com", PaymentMethod.CASH, PaymentStatus.PENDING,
                        1, "user-1", ShippingCarrier.UPS), "pay-7"),
                Instant.now().minus(5, ChronoUnit.SECONDS));
        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(recent));

        paymentService.resolvePendingManualPayments();

        assertThat(recent.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void approve_awaitingReview_completesAndPublishesPaymentCompleted() {
        Payment awaiting = withId(new Payment("order-1", "a@b.com", PaymentMethod.BANK_TRANSFER,
                PaymentStatus.AWAITING_REVIEW, 4, "user-1", ShippingCarrier.DHL), "pay-8");
        when(paymentRepository.findByIdAndDeletedFalse("pay-8")).thenReturn(Optional.of(awaiting));

        paymentService.approve("pay-8");

        assertThat(awaiting.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository).save(awaiting);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_COMPLETED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("paymentId", "pay-8").containsEntry("userId", "user-1")
                .containsEntry("shippingCarrier", "DHL").containsEntry("quantity", 4);
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_COMPLETED, payload)).isEmpty();
    }

    @Test
    void approve_notAwaitingReview_throwsConflict() {
        Payment pending = withId(new Payment("order-1", "a@b.com", PaymentMethod.CASH,
                PaymentStatus.PENDING, 1, "user-1", ShippingCarrier.UPS), "pay-9");
        when(paymentRepository.findByIdAndDeletedFalse("pay-9")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentService.approve("pay-9")).isInstanceOf(ResponseStatusException.class);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void reject_awaitingReview_failsAndPublishesPaymentFailedWithReason() {
        Payment awaiting = withId(new Payment("order-1", "a@b.com", PaymentMethod.CASH,
                PaymentStatus.AWAITING_REVIEW, 2, "user-1", ShippingCarrier.UPS), "pay-10");
        when(paymentRepository.findByIdAndDeletedFalse("pay-10")).thenReturn(Optional.of(awaiting));

        paymentService.reject("pay-10", "proof does not match order total");

        assertThat(awaiting.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(awaiting.getFailureReason()).isEqualTo("proof does not match order total");
        verify(paymentRepository).save(awaiting);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_FAILED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("paymentId", "pay-10");
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_FAILED, payload)).isEmpty();
    }

    @Test
    void reject_noReasonGiven_defaultsToRejectedByFinance() {
        Payment awaiting = withId(new Payment("order-1", "a@b.com", PaymentMethod.CASH,
                PaymentStatus.AWAITING_REVIEW, 2, "user-1", ShippingCarrier.UPS), "pay-11");
        when(paymentRepository.findByIdAndDeletedFalse("pay-11")).thenReturn(Optional.of(awaiting));

        paymentService.reject("pay-11", null);

        assertThat(awaiting.getFailureReason()).isEqualTo("rejected_by_finance");
    }

    @Test
    void refund_paymentFound_marksRefundedAndPublishesPaymentRefunded() {
        Payment payment = withId(new Payment("order-1", "a@b.com", PaymentMethod.CREDIT_CARD, PaymentStatus.COMPLETED, null), "pay-1");
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));

        paymentService.refund("order-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENT_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.PAYMENT_REFUNDED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.PAYMENT_REFUNDED, payload)).isEmpty();
    }

    @Test
    void refund_paymentNotFound_isNoOp() {
        when(paymentRepository.findByOrderId("order-missing")).thenReturn(Optional.empty());

        paymentService.refund("order-missing");

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void getByOrderId_found_returnsPayment() {
        Payment payment = withId(new Payment("order-1", "a@b.com", PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING, 1, "user-1", ShippingCarrier.UPS), "pay-12");
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));

        assertThat(paymentService.getByOrderId("order-1")).isEqualTo(payment);
    }

    @Test
    void getByOrderId_notFound_throwsNotFound() {
        when(paymentRepository.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByOrderId("missing")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attachProof_ownPayment_setsUrl() {
        Payment payment = withId(new Payment("order-1", "a@b.com", PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING, 1, "user-1", ShippingCarrier.UPS), "pay-13");
        when(paymentRepository.findByIdAndDeletedFalse("pay-13")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment result = paymentService.attachProof("user-1", "pay-13", "/api/payments/files/abc.jpg");

        assertThat(result.getProofOfPaymentUrl()).isEqualTo("/api/payments/files/abc.jpg");
        verify(paymentRepository).save(payment);
    }

    @Test
    void attachProof_notOwnPayment_throwsForbidden() {
        Payment payment = withId(new Payment("order-1", "a@b.com", PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING, 1, "user-1", ShippingCarrier.UPS), "pay-14");
        when(paymentRepository.findByIdAndDeletedFalse("pay-14")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.attachProof("someone-else", "pay-14", "/api/payments/files/abc.jpg"))
                .isInstanceOf(ResponseStatusException.class);
        verify(paymentRepository, never()).save(any());
    }
}
