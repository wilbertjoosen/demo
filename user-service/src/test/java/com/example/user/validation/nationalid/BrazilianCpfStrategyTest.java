package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrazilianCpfStrategyTest {

    private final BrazilianCpfStrategy strategy = new BrazilianCpfStrategy();

    @Test
    void countryCode_isBR() {
        assertThat(strategy.countryCode()).isEqualTo("BR");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isValid_blankOrNull_rejectsWithBlankMessage(String nationalId) {
        ValidationResult result = strategy.isValid(nationalId);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("CPF cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11144477735", "52998224725"})
    void isValid_validCpf_passesBothCheckDigits(String cpf) {
        ValidationResult result = strategy.isValid(cpf);

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"111.444.777-35", "111 444 777 35"})
    void isValid_formattedWithSeparators_stripsNonDigitsBeforeValidating(String cpf) {
        ValidationResult result = strategy.isValid(cpf);

        assertThat(result.valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1114447773", "111444777356"})
    void isValid_wrongLength_rejectsWithFormatMessage(String cpf) {
        ValidationResult result = strategy.isValid(cpf);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid CPF format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "00000000000", "99999999999"})
    void isValid_repeatedDigitSequence_rejectsWithFormatMessage(String cpf) {
        ValidationResult result = strategy.isValid(cpf);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid CPF format");
    }

    @Test
    void isValid_failsCheckDigits_rejectsWithDigitsMessage() {
        // 11144477735 is valid; flipping the final check digit breaks the second verifier digit
        ValidationResult result = strategy.isValid("11144477736");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid CPF digits");
    }
}
