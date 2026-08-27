package com.example.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddStockRequest(@NotBlank String warehouseId, @Min(1) int quantity) {
}
