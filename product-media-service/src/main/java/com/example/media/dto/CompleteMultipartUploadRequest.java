package com.example.media.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CompleteMultipartUploadRequest(
        @NotBlank String key,
        @NotBlank String uploadId,
        @NotEmpty @Valid List<CompletedPart> parts) {

    public record CompletedPart(int partNumber, @NotBlank String eTag) {
    }
}
