package com.example.media.controller;

import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;
import com.example.media.model.MediaModelAssembler;
import com.example.media.service.MediaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final MediaModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<MediaAsset>> list(@RequestParam String productId) {
        return assembler.toCollectionModel(mediaService.listByProduct(productId));
    }

    public record CreateMediaRequest(@NotBlank String productId, @NotNull MediaType type, @NotBlank String url, String caption) {
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<EntityModel<MediaAsset>> create(@Valid @RequestBody CreateMediaRequest request) {
        MediaAsset created = mediaService.create(request.productId(), request.type(), request.url(), request.caption());
        return ResponseEntity.status(201).body(assembler.toModel(created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
