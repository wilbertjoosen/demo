package com.example.media.model;
import com.example.media.enums.MediaType;
import com.example.media.enums.MediaValidationStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * findByProductIdAndDeletedFalseOrderByPositionAsc(productId) and
 * countByProductIdAndDeletedFalse(productId).
 */
@CompoundIndex(def = "{'productId': 1, 'deleted': 1, 'position': 1}")
@Document(collection = "media_assets")
@Getter
@NoArgsConstructor
public class MediaAsset {

    @Id
    private String id;

    private String productId;
    private MediaType type;
    private String url;
    private String fileMame;
    private String path;
    private String caption;
    private int position;

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

    /**
     * ACTIVE by default: the old direct-through-the-backend upload path (MediaFileStorageService)
     * already has the file's bytes in this JVM before ever calling create() — nothing to stage or
     * validate asynchronously. Only assets from the presigned upload flow start at
     * PENDING_VALIDATION (see the other constructor and MediaValidationListener).
     */
    private MediaValidationStatus validationStatus = MediaValidationStatus.ACTIVE;

    public MediaAsset(String productId, MediaType type, String url, String fileMame, String path, String caption, int position) {
        this.productId = productId;
        this.type = type;
        this.url = url;
        this.fileMame = fileMame;
        this.path = path;
        this.caption = caption;
        this.position = position;
    }

    /** Presigned upload flow: starts PENDING_VALIDATION, {@code url} points at the staging bucket until {@link #activate}. */
    public MediaAsset(String productId, MediaType type, String stagingUrl, String fileMame, String path, String caption,
            int position, MediaValidationStatus validationStatus) {
        this(productId, type, stagingUrl, fileMame, path, caption, position);
        this.validationStatus = validationStatus;
    }

    /** Validation passed: the object has been copied to the production bucket at {@code productionUrl}. */
    public void activate(String productionUrl) {
        this.url = productionUrl;
        this.validationStatus = MediaValidationStatus.ACTIVE;
    }

    /** Validation failed — the staging object is deleted by the caller; this just records the outcome. */
    public void reject() {
        this.validationStatus = MediaValidationStatus.REJECTED;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
