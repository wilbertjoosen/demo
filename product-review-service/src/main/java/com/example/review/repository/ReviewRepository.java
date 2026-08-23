package com.example.review.repository;

import com.example.review.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    List<Review> findByProductIdAndDeletedFalseOrderByCreatedAtDesc(String productId);

    Optional<Review> findByIdAndDeletedFalse(String id);

    Optional<Review> findByProductIdAndKeycloakUserIdAndDeletedFalse(String productId, String keycloakUserId);
}
