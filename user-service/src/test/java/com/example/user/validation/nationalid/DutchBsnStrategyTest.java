package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DutchBsnStrategyTest {

    private final DutchBsnStrategy strategy = new DutchBsnStrategy();

    @Test
    void countryCode_isNL() {
        assertThat(strategy.countryCode()).isEqualTo("NL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isValid_blankOrNull_rejectsWithBlankMessage(String nationalId) {
        ValidationResult result = strategy.isValid(nationalId);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("ID cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"111222333", "123456782", "111111110"})
    void isValid_validBsn_passesElfproef(String bsn) {
        ValidationResult result = strategy.isValid(bsn);

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"111 222 333", "111-222-333", "111.222.333"})
    void isValid_formattedWithSeparators_stripsNonDigitsBeforeValidating(String bsn) {
        ValidationResult result = strategy.isValid(bsn);

        assertThat(result.valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "1234567890", "12345678a"})
    void isValid_wrongLength_rejectsWithFormatMessage(String bsn) {
        ValidationResult result = strategy.isValid(bsn);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_allZeros_rejectedAsDummySequence() {
        ValidationResult result = strategy.isValid("000000000");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_failsElfproefChecksum_rejectsWithDigitsMessage() {
        // 111222333 is valid; bumping the last digit breaks the -1-weighted check digit
        ValidationResult result = strategy.isValid("111222334");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID digits");
    }
}
