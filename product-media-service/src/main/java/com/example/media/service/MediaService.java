package com.example.media.service;
import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;

import java.util.List;

public interface MediaService {

    MediaAsset create(String productId, MediaType type, String url, String fileName, String caption);

    /** Presigned upload flow: creates the asset PENDING_VALIDATION, url pointing at the staging bucket. */
    MediaAsset createPendingValidation(String productId, MediaType type, String stagingUrl, String fileName, String caption);

    /** MediaValidationListener: validation passed, the object now lives at productionUrl. */
    void activate(String mediaAssetId, String productionUrl);

    /** MediaValidationListener: validation failed — soft-deletes the asset (staging object cleanup is the caller's job). */
    void reject(String mediaAssetId);

    /** Every non-deleted line for a product regardless of validation status — see MediaAssetRepository's javadoc. */
    List<MediaAsset> listAllByProduct(String productId);

    List<MediaAsset> listByProduct(String productId);

    void delete(String id);
}
