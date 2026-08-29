package com.example.review.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * findByProductIdAndDeletedFalseOrderByCreatedAtDesc(productId) and
 * findByProductIdAndKeycloakUserIdAndDeletedFalse(productId, keycloakUserId).
 */
@CompoundIndexes({
        @CompoundIndex(def = "{'productId': 1, 'deleted': 1, 'createdAt': -1}"),
        @CompoundIndex(def = "{'productId': 1, 'keycloakUserId': 1, 'deleted': 1}")
})
@Document(collection = "reviews")
@Getter
@NoArgsConstructor
public class Review {

    @Id
    private String id;

    private String productId;
    private String keycloakUserId;
    private String authorName;
    private int rating;
    private String body;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    private boolean deleted = false;
    private Instant deletedAt;

    public Review(String productId, String keycloakUserId, String authorName, int rating, String body) {
        this.productId = productId;
        this.keycloakUserId = keycloakUserId;
        this.authorName = authorName;
        this.rating = rating;
        this.body = body;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
