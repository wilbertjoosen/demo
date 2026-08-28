package com.example.review.repository;
import com.example.review.model.Review;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    /** Public review listing — see product-service's ProductRepository for the full reasoning on this pattern. */
    @ReadPreference("secondaryPreferred")
    List<Review> findByProductIdAndDeletedFalseOrderByCreatedAtDesc(String productId);

    Optional<Review> findByIdAndDeletedFalse(String id);

    Optional<Review> findByProductIdAndKeycloakUserIdAndDeletedFalse(String productId, String keycloakUserId);
}
