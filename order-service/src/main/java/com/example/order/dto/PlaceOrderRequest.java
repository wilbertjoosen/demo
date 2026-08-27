package com.example.order.dto;

import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotBlank String productId,
                                @Min(1) int quantity,
                                @NotNull @Valid Address shippingAddress,
                                @NotNull PaymentMethod paymentMethod,
                                @NotNull ShippingCarrier shippingCarrier) {
}
