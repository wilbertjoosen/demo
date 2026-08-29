package com.example.media.dto;

import com.example.media.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Called after the presigned upload (single-part PUT, or multipart + complete) has finished. */
public record ConfirmStagedUploadRequest(
        @NotBlank String productId,
        @NotNull MediaType type,
        @NotBlank String key,
        @NotBlank String fileName,
        String caption) {
}
