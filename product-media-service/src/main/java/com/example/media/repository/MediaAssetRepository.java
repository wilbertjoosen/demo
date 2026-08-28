package com.example.media.repository;
import com.example.media.enums.MediaValidationStatus;
import com.example.media.model.MediaAsset;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {

    /** Every non-deleted line for a product regardless of validation status — used internally (e.g. cleanup on PRODUCT_DELETED). */
    List<MediaAsset> findByProductIdAndDeletedFalseOrderByPositionAsc(String productId);

    /**
     * What listByProduct() actually returns — PENDING_VALIDATION/REJECTED assets never show up in
     * a listing. This is the public media listing — see product-service's ProductRepository for
     * the full reasoning on the {@code secondaryPreferred} pattern.
     */
    @ReadPreference("secondaryPreferred")
    List<MediaAsset> findByProductIdAndValidationStatusAndDeletedFalseOrderByPositionAsc(
            String productId, MediaValidationStatus validationStatus);

    Optional<MediaAsset> findByIdAndDeletedFalse(String id);

    int countByProductIdAndDeletedFalse(String productId);
}
