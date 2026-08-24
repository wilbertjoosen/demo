package com.example.user.validation;

import com.example.user.dto.ValidationResult;
import com.example.user.validation.nationalid.NationalIdStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NationalIdValidationServiceTest {

    private NationalIdValidationService service;

    @BeforeEach
    void setUp() {
        service = new NationalIdValidationService(List.of(
                stubStrategy("NL", "111222333"),
                stubStrategy("DE", "12345678911")
        ));
    }

    private NationalIdStrategy stubStrategy(String country, String validId) {
        return new NationalIdStrategy() {
            @Override
            public String countryCode() {
                return country;
            }

            @Override
            public ValidationResult isValid(String nationalId) {
                return validId.equals(nationalId)
                        ? new ValidationResult(true, null)
                        : new ValidationResult(false, "Invalid " + country + " id");
            }
        };
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isValid_noCountry_acceptsAnyId(String country) {
        ValidationResult result = service.isValid(country, "anything");

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @Test
    void isValid_knownCountry_delegatesToMatchingStrategy() {
        assertThat(service.isValid("NL", "111222333").valid()).isTrue();
        assertThat(service.isValid("NL", "000000000").valid()).isFalse();
    }

    @Test
    void isValid_countryCodeIsCaseInsensitive() {
        ValidationResult result = service.isValid("nl", "111222333");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void isValid_unsupportedCountry_fallsBackToAcceptingUnvalidated() {
        // "Option B": no strategy registered for this country, so validation is skipped
        // rather than rejecting the user outright.
        ValidationResult result = service.isValid("XX", "whatever-format");

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }
}
