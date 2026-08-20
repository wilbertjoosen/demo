package com.example.user.dto.request;

import com.example.common.model.Address;
import com.example.user.model.ProfileFields;
import com.example.user.validation.ValidNationalId;

import java.util.Map;

@ValidNationalId
public record UpdateProfileRequest(
        String nationalIdCountry,
        String nationalId,
        String phone,
        Address shippingAddress,
        Map<String, String> customAttributes
) {

    public ProfileFields toFields() {
        return new ProfileFields(
                shippingAddress,
                nationalId,
                phone,
                customAttributes
        );
    }
}