package com.example.common.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared between user-service (profile default address) and order-service (per-order shipping
 * snapshot) — an explicit exception to "don't share domain types across services" made because the
 * two really are the same concept with no expected divergence; @Embeddable only matters to
 * order-service's JPA entity, Spring Data MongoDB (user-service) ignores it.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @NotBlank
    private String street;

    @NotBlank
    private String city;

    @NotBlank
    private String postalCode;

    @NotBlank
    private String country;

    public Address(String street, String city, String postalCode, String country) {
        this.street = street;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
    }
}
