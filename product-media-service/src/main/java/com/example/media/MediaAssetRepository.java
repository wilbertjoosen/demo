package com.example.media;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {

    List<MediaAsset> findByProductIdAndDeletedFalseOrderByPositionAsc(String productId);

    Optional<MediaAsset> findByIdAndDeletedFalse(String id);

    int countByProductIdAndDeletedFalse(String productId);
}
