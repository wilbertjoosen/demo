package com.example.delivery.repository;

import com.example.delivery.enums.DeliveryStatus;
import com.example.delivery.model.Delivery;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    List<Delivery> findByDeletedFalse();

    Optional<Delivery> findByIdAndDeletedFalse(String id);

    List<Delivery> findByStatus(DeliveryStatus status);
}
