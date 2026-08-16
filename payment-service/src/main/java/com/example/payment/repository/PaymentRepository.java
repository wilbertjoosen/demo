package com.example.payment.repository;

import com.example.payment.model.Payment;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    /** Not filtered by deleted — used internally by the saga's refund lookup, not the public API. */
    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByDeletedFalse();

    Optional<Payment> findByIdAndDeletedFalse(String id);
}
