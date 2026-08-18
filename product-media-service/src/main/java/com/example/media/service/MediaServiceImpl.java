package com.example.media.service;

import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;
import com.example.media.ports.StoragePort;
import com.example.media.repository.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MediaServiceImpl implements MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final StoragePort storage;
    @Value("${media.upload-dir}")
    private final String uploadDir;

    public MediaServiceImpl(@Value("${media.upload-dir}") String uploadDir,
                            MediaAssetRepository mediaAssetRepository,
                            StoragePort storage) {
        this.uploadDir = uploadDir;
        this.mediaAssetRepository = mediaAssetRepository;
        this.storage = storage;
    }


    @Override
    public MediaAsset create(String productId, MediaType type, String url, String fileName, String caption) {
        int nextPosition = mediaAssetRepository.countByProductIdAndDeletedFalse(productId);
        return mediaAssetRepository.save(new MediaAsset(productId, type, url, fileName, uploadDir, caption, nextPosition));
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
        String fullPath = String.join("/", mediaAsset.getPath(), mediaAsset.getFileMame());
        storage.deleteFile(fullPath);
        mediaAssetRepository.save(mediaAsset);
    }
}
