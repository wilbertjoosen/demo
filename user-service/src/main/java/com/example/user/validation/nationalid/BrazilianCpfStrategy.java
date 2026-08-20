package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class BrazilianCpfStrategy implements NationalIdStrategy {

    @Override
    public String countryCode() {
        return "BR";
    }

    @Override
    public ValidationResult isValid(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return new ValidationResult(false, "CPF cannot be blank");
        }

        // Clean formatting characters (dots and dashes) if present
        String cleanCpf = nationalId.replaceAll("\\D", "");

        if (!isValidCpfFormat(cleanCpf)) {
            return new ValidationResult(false, "Invalid CPF format");
        }

        if (!hasValidCpfDigits(cleanCpf)) {
            return new ValidationResult(false, "Invalid CPF digits");
        }

        return new ValidationResult(true, null);
    }

    private boolean isValidCpfFormat(String cpf) {
        // Must be 11 digits and cannot be a sequence of identical digits (e.g., 111.111.111-11)
        if (cpf.length() != 11 || cpf.matches("(\\d)\\1{10}")) {
            return false;
        }
        return true;
    }

    private boolean hasValidCpfDigits(String cpf) {
        try {
            // First verifier digit
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
            }
            int firstDigit = 11 - (sum % 11);
            if (firstDigit >= 10) {
                firstDigit = 0;
            }

            if (firstDigit != Character.getNumericValue(cpf.charAt(9))) {
                return false;
            }

            // Second verifier digit
            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
            }
            int secondDigit = 11 - (sum % 11);
            if (secondDigit >= 10) {
                secondDigit = 0;
            }

            return secondDigit == Character.getNumericValue(cpf.charAt(10));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
