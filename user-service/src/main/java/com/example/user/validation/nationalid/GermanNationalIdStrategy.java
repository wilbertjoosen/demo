package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.springframework.stereotype.Component;

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

    private boolean isValidIdFormat(String id) {
        return true;
    }

    private boolean hasValidIdDigits(String id) {
        return true;
    }
}
