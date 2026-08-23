package com.example.order.repository;

import com.example.order.model.OrderView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderViewRepository extends MongoRepository<OrderView, String> {

    List<OrderView> findByKeycloakUserIdAndDeletedFalse(String keycloakUserId);

    Optional<OrderView> findByIdAndDeletedFalse(String id);
}
