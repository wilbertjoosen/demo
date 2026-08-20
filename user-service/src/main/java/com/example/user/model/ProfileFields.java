package com.example.user.model;

import com.example.common.model.Address;

import java.util.Map;

/**
 * The subset of User fields an update (self-service or admin) can set. Deliberately excludes
 * username/email/firstName/lastName — those live in Keycloak and are changed there, not here.
 */
public record ProfileFields(Address shippingAddress, String nationalId, String phone,
                             Map<String, String> customAttributes) {

    public ProfileFields {
        shippingAddress = blankToNull(shippingAddress);
    }

    /** The frontend's optional-address forms submit an all-blank object rather than omitting the field. */
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
