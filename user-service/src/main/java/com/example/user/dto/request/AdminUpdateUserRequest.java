package com.example.user.dto.request;

import com.example.common.model.Address;
import com.example.user.model.IdentityFields;
import com.example.user.model.ProfileFields;
import jakarta.validation.constraints.Email;

import java.util.Map;

public record AdminUpdateUserRequest(Address shippingAddress, String nationalId, String phone,
                                     Map<String, String> customAttributes, String username,
                                     @Email String email, String firstName, String lastName) {
    public ProfileFields toFields() {
        return new ProfileFields(shippingAddress, nationalId, phone, customAttributes);
    }

    public IdentityFields toIdentity() {
        if (username == null && email == null && firstName == null && lastName == null) {
            return null;
        }
        return new IdentityFields(username, email, firstName, lastName);
    }
}
