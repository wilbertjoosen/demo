package com.example.media.dto;

import com.example.media.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMediaRequest(
        @NotBlank String productId,
        @NotNull MediaType type,
        @NotBlank String fileName,
        @NotBlank String url,
        String caption) {
}