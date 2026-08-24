package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class DutchBsnStrategy implements NationalIdStrategy {

    @Override
    public String countryCode() {
        return "NL";
    }

    @Override
    public ValidationResult isValid(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return new ValidationResult(false, "ID cannot be blank");
        }

        // Strip out all non-digits (spaces, dashes, dots, etc.) uniformly up front
        String cleanId = nationalId.replaceAll("\\D", "");

        if (!isValidIdFormat(cleanId)) {
            return new ValidationResult(false, "Invalid ID format");
        }

        if (!hasValidIdDigits(cleanId)) {
            return new ValidationResult(false, "Invalid ID digits");
        }

        return new ValidationResult(true, null);
    }

    /**
     * Checks if the clean ID follows the strict structural and length rules of a BSN.
     */
    private boolean isValidIdFormat(String id) {
        if (id == null) {
            return false;
        }

        // BSN must contain exactly 9 numeric digits
        if (!id.matches("^\\d{9}$")) {
            return false;
        }

        // Avoid obvious dummy test sequences (e.g., all zeros)
        return !id.equals("000000000");
    }

    /**
     * Validates the numerical integrity of the BSN using the official Dutch Elfproef (Eleven-proof) algorithm.
     */
    private boolean hasValidIdDigits(String id) {
        if (id == null || id.length() != 9) {
            return false;
        }

        int sum = 0;
        int position = 9; // The multiplier starts at 9 for the first digit

        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(id.charAt(i));

            if (i == 8) {
                // The last digit (index 8) has a negative weight of -1
                sum += (digit * -1);
            } else {
                // The remaining digits have a descending weight from 9 down to 2
                sum += (digit * position);
            }
            position--;
        }

        // If the total accumulated sum is divisible by 11, the BSN digits are mathematically valid
        return (sum % 11 == 0);
    }
}
