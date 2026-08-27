package com.example.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(@NotBlank String body) {
}
