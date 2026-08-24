package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class GermanNationalIdStrategy implements NationalIdStrategy {

    @Override
    public String countryCode() {
        return "DE";
    }

    @Override
    public ValidationResult isValid(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return new ValidationResult(false, "ID cannot be blank");
        }

        // Clean formatting characters (dots and dashes) if present
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
     * Validates the structural, length, and repetition constraints of the German IdNr.
     */
    private boolean isValidIdFormat(String id) {
        if (id == null || !id.matches("^[1-9]\\d{10}$")) {
            return false;
        }

        // Isolate the first 10 structural digits
        String structuralPart = id.substring(0, 10);

        // Extract frequency counts for each digit
        Map<Character, Integer> digitCounts = countDigitOccurrences(structuralPart);

        // Directly combine validations into a single return expression
        return hasValidRepetitionPattern(digitCounts)
                && !(digitCounts.containsValue(3) && hasConsecutiveTriple(structuralPart));
    }

    /**
     * Helper method to map the frequency of each digit within the structural part.
     */
    private Map<Character, Integer> countDigitOccurrences(String structuralPart) {
        Map<Character, Integer> counts = new HashMap<>();
        for (char ch : structuralPart.toCharArray()) {
            counts.put(ch, counts.getOrDefault(ch, 0) + 1);
        }
        return counts;
    }

    /**
     * Verifies that exactly one digit appears either twice or thrice, and no digit appears more than thrice.
     */
    private boolean hasValidRepetitionPattern(Map<Character, Integer> digitCounts) {
        int doubleCounts = 0;
        int tripleCounts = 0;

        for (int count : digitCounts.values()) {
            if (count == 2) {
                doubleCounts++;
            } else if (count == 3) {
                tripleCounts++;
            } else if (count > 3) {
                return false; // No single digit can appear more than 3 times
            }
        }

        // Refactored to a single return statement evaluating the valid combinations
        return (doubleCounts == 1 && tripleCounts == 0) || (doubleCounts == 0 && tripleCounts == 1);
    }

    /**
     * Checks if the structural part contains three identical consecutive digits (e.g., "111").
     */
    private boolean hasConsecutiveTriple(String structuralPart) {
        for (int i = 0; i < structuralPart.length() - 2; i++) {
            if (structuralPart.charAt(i) == structuralPart.charAt(i + 1) &&
                    structuralPart.charAt(i) == structuralPart.charAt(i + 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates the 11th digit using the official ISO 7064 Mod 11, 10 Check Digit algorithm.
     */
    private boolean hasValidIdDigits(String id) {
        if (id == null || id.length() != 11) {
            return false;
        }

        int product = 10;

        // Loop through the first 10 digits
        for (int i = 0; i < 10; i++) {
            int digit = Character.getNumericValue(id.charAt(i));

            int sum = (digit + product) % 10;
            if (sum == 0) {
                sum = 10;
            }

            product = (sum * 2) % 11;
        }

        // Calculate expected check digit from the remaining remainder
        int expectedCheckDigit = (11 - product) == 10 ? 0 : (11 - product);

        // Extracted check digit (11th character)
        int actualCheckDigit = Character.getNumericValue(id.charAt(10));

        // Refactored to a single direct return comparison
        return actualCheckDigit == expectedCheckDigit;
    }
}
