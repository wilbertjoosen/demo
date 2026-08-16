package com.example.user.model;

import com.example.common.model.Address;

import java.time.Instant;
import java.util.Map;

/** The API-facing shape: Mongo-stored profile fields merged with identity fields read live from Keycloak. */
public record UserProfileView(String id, String keycloakId, String username, String email, String firstName,
                               String lastName, Address shippingAddress, String nationalId, String phone,
                               Map<String, String> customAttributes, Instant createdAt, Instant updatedAt) {

    public static UserProfileView merge(User user, KeycloakUserSummary identity) {
        String username = identity == null ? "(unknown)" : identity.username();
        String email = identity == null ? null : identity.email();
        String firstName = identity == null ? null : identity.firstName();
        String lastName = identity == null ? null : identity.lastName();
        return new UserProfileView(user.getId(), user.getKeycloakId(), username, email, firstName, lastName,
                user.getShippingAddress(), user.getNationalId(), user.getPhone(), user.getCustomAttributes(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
