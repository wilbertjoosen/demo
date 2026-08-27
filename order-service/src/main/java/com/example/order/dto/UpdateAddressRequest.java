package com.example.order.dto;

import com.example.common.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateAddressRequest(@NotNull @Valid Address shippingAddress) {
}
