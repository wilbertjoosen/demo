package com.example.review.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateReviewRequest(@NotBlank String productId, @Min(1) @Max(5) int rating, String body) {
}
