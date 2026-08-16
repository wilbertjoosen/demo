package com.example.user.model;

import com.example.common.model.Address;

import java.util.Map;

/**
 * The subset of User fields an update (self-service or admin) can set. Deliberately excludes
 * username/email/firstName/lastName — those live in Keycloak and are changed there, not here.
 */
public record ProfileFields(Address shippingAddress, String nationalId, String phone,
                             Map<String, String> customAttributes) {
}
