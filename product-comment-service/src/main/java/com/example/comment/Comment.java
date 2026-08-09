package com.example.comment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One-level-deep threading only ({@code parentId} points at a top-level comment, never another
 * reply) — enough to demonstrate the pattern without a recursive tree API.
 */
@Document(collection = "comments")
@Getter
@NoArgsConstructor
public class Comment {

    @Id
    private String id;

    private String productId;
    private String parentId;
    private String keycloakUserId;
    private String authorName;
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

    public Comment(String productId, String parentId, String keycloakUserId, String authorName, String body) {
        this.productId = productId;
        this.parentId = parentId;
        this.keycloakUserId = keycloakUserId;
        this.authorName = authorName;
        this.body = body;
    }

    void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    void updateBody(String body) {
        this.body = body;
    }
}
