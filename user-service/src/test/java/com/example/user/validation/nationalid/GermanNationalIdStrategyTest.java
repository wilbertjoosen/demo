package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GermanNationalIdStrategyTest {

    private final GermanNationalIdStrategy strategy = new GermanNationalIdStrategy();

    @Test
    void countryCode_isDE() {
        assertThat(strategy.countryCode()).isEqualTo("DE");
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
    @ValueSource(strings = {
            "12345678911", // one digit repeated exactly twice
            "57864213117", // one digit repeated exactly thrice, non-consecutive
    })
    void isValid_validIdNr_passesStructureAndChecksum(String idNr) {
        ValidationResult result = strategy.isValid(idNr);

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123.456.789-11", "123 456 789 11"})
    void isValid_formattedWithSeparators_stripsNonDigitsBeforeValidating(String idNr) {
        ValidationResult result = strategy.isValid(idNr);

        assertThat(result.valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567891", "123456789111", "0234567891"})
    void isValid_wrongLengthOrLeadingZero_rejectsWithFormatMessage(String idNr) {
        ValidationResult result = strategy.isValid(idNr);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_noDigitRepeated_rejectsWithFormatMessage() {
        // Structural part 1234567890 has ten distinct digits, so no digit repeats at all.
        ValidationResult result = strategy.isValid("12345678904");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_twoDigitsEachRepeatedTwice_rejectsWithFormatMessage() {
        // Structural part 1122345678 has two digits ('1' and '2') each repeated twice.
        ValidationResult result = strategy.isValid("11223456781");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_tripleAppearsConsecutively_rejectsWithFormatMessage() {
        // Structural part 1112345678 has '1' repeated thrice, consecutively at the start.
        ValidationResult result = strategy.isValid("11123456781");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID format");
    }

    @Test
    void isValid_failsChecksum_rejectsWithDigitsMessage() {
        // 12345678911 is a valid IdNr; flipping the check digit breaks ISO 7064 Mod 11,10.
        ValidationResult result = strategy.isValid("12345678912");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid ID digits");
    }
}
