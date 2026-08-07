package com.example.user;

import com.example.common.model.Address;

import java.util.Map;

/** The subset of User fields an update (self-service or admin) can set — everything except identity (id/keycloakId). */
public record ProfileFields(String displayName, Address shippingAddress, String nationalId, String phone,
                             Map<String, String> customAttributes) {
}
