package com.example.media.controller;

import com.example.media.service.MediaFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;

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
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        String filename = storageService.store(file);
        return Map.of("url", "/api/media/files/" + filename);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        Path path = storageService.resolve(filename);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
