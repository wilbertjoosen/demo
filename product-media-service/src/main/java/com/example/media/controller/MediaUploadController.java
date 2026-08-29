package com.example.media.controller;

import com.example.media.dto.CompleteMultipartUploadRequest;
import com.example.media.dto.ConfirmStagedUploadRequest;
import com.example.media.dto.MediaFileResponse;
import com.example.media.dto.PresignedUploadRequest;
import com.example.media.dto.PresignedUploadResponse;
import com.example.media.model.MediaAsset;
import com.example.media.service.MediaFileStorageService;
import com.example.media.service.PresignedMediaUploadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * The old {@code /upload}'s response "url" is a path relative to this service's own routing prefix
 * (already proxied by the gateway at /api/media/**), not an absolute URL. The new
 * {@code /presigned-upload/*} endpoints below hand back real, absolute S3 URLs instead — the
 * frontend PUTs directly to them, this service's app server never sees the file's bytes.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaFileStorageService storageService;
    private final PresignedMediaUploadService presignedMediaUploadService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<MediaFileResponse> upload(@RequestParam("file") MultipartFile file) {
        try {
            MediaFileResponse response = storageService.store(file);
            return ResponseEntity.ok(response);
        }catch (IOException e){
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/presigned-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<PresignedUploadResponse> presignUpload(@Valid @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(presignedMediaUploadService.initiate(request));
    }

    @PostMapping("/presigned-upload/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Void> completeMultipartUpload(@Valid @RequestBody CompleteMultipartUploadRequest request) {
        presignedMediaUploadService.completeMultipart(request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/presigned-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Void> abortMultipartUpload(@RequestParam String key, @RequestParam String uploadId) {
        presignedMediaUploadService.abortMultipart(key, uploadId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presigned-upload/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<MediaAsset> confirmStagedUpload(@Valid @RequestBody ConfirmStagedUploadRequest request) {
        return ResponseEntity.status(201).body(presignedMediaUploadService.confirm(request));
    }
}
