package com.example.media.service;

import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;
import com.example.media.repository.MediaAssetRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetRepository mediaAssetRepository;

    @Override
    public MediaAsset create(String productId, MediaType type, String url, String caption) {
        int nextPosition = mediaAssetRepository.countByProductIdAndDeletedFalse(productId);
        return mediaAssetRepository.save(new MediaAsset(productId, type, url, caption, nextPosition));
    }

    @Override
    public List<MediaAsset> listByProduct(String productId) {
        return mediaAssetRepository.findByProductIdAndDeletedFalseOrderByPositionAsc(productId);
    }

    @Override
    public void delete(String id) {
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        mediaAsset.markDeleted();
        mediaAssetRepository.save(mediaAsset);
    }
}
