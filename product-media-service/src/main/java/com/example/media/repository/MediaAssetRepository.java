package com.example.media.repository;
import com.example.media.model.MediaAsset;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {

    /** Public media listing — see product-service's ProductRepository for the full reasoning on this pattern. */
    @ReadPreference("secondaryPreferred")
    List<MediaAsset> findByProductIdAndDeletedFalseOrderByPositionAsc(String productId);

    Optional<MediaAsset> findByIdAndDeletedFalse(String id);

    int countByProductIdAndDeletedFalse(String productId);
}
