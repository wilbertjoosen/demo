package com.example.media.controller;

import com.example.media.dto.MediaFileResponse;
import com.example.media.service.MediaFileStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * The upload response's "url" is a path relative to this service's own routing prefix (already
 * proxied by the gateway at /api/media/**), not an absolute URL — the frontend prepends its own API
 * base before storing it as a MediaAsset.url, same as it would for an externally-hosted link.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaFileStorageService storageService;

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
}
