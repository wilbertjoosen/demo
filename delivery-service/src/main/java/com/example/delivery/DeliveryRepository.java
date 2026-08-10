package com.example.delivery;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    List<Delivery> findByDeletedFalse();

    Optional<Delivery> findByIdAndDeletedFalse(String id);

    List<Delivery> findByStatus(DeliveryStatus status);
}
