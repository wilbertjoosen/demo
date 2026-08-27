package com.example.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank String productId, String parentId, @NotBlank String body) {
}
