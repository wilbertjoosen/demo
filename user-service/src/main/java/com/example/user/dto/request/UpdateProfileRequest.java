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
                blankToNull(shippingAddress),
                nationalId,
                phone,
                customAttributes
        );
    }

    private static Address blankToNull(Address address) {
        if (address == null) {
            return null;
        }

        boolean allBlank =
                isBlank(address.getStreet())
                        && isBlank(address.getCity())
                        && isBlank(address.getPostalCode())
                        && isBlank(address.getCountry());

        return allBlank ? null : address;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}