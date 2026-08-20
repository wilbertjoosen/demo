package com.example.user.dto;

public record ValidationResult(
        boolean valid,
        String message
) {}