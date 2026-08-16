package com.example.media.model;

import com.example.media.enums.MediaType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** {@code url} points at an externally-hosted asset (no file upload/storage backend in this demo). */
@Document(collection = "media_assets")
@Getter
@NoArgsConstructor
public class MediaAsset {

    @Id
    private String id;

    private String productId;
    private MediaType type;
    private String url;
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

    public MediaAsset(String productId, MediaType type, String url, String caption, int position) {
        this.productId = productId;
        this.type = type;
        this.url = url;
        this.caption = caption;
        this.position = position;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
