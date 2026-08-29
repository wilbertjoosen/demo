package com.example.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive long fileSizeBytes) {
}
