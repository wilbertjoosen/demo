package com.example.user.dto.request;

import com.example.common.model.Address;
import com.example.user.model.ProfileFields;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateUserRequest(@NotBlank String username, @NotBlank @Email String email, @NotBlank String firstName,
                                @NotBlank String lastName, @NotBlank String password, Address shippingAddress,
                                String nationalId, String phone, Map<String, String> customAttributes) {
    public ProfileFields toFields() {
        return new ProfileFields(blankToNull(shippingAddress), nationalId, phone, customAttributes);
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


