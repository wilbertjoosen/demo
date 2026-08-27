package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class UsSsnStrategy implements NationalIdStrategy {

    @Override
    public String countryCode() {
        return "US";
    }

    @Override
    public ValidationResult isValid(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return new ValidationResult(false, "SSN cannot be blank");
        }

        // Clean formatting characters (hyphens, spaces) if present
        String cleanSsn = nationalId.replaceAll("\\D", "");

        if (cleanSsn.length() != 9) {
            return new ValidationResult(false, "Invalid SSN format: Must be exactly 9 digits");
        }

        return validateSsnRules(cleanSsn);
    }

    /**
     * Validates the SSN against official SSA (Social Security Administration)
     * structural constraints and high-frequency invalid fake patterns.
     */
    private ValidationResult validateSsnRules(String ssn) {
        // Extract the three distinct segments: Area (3), Group (2), Serial (4)
        String areaGroup = ssn.substring(0, 3);
        String groupGroup = ssn.substring(3, 5);
        String serialGroup = ssn.substring(5, 9);

        // 1. Check for identical sequences (e.g., 000000000, 111111111)
        if (ssn.matches("(\\d)\\1{8}")) {
            return new ValidationResult(false, "Invalid SSN digits: Repeated sequences are invalid");
        }

        // 2. Area Number Constraints (First 3 digits)
        // - Cannot be '000'
        // - Cannot be '666' (Excluded by SSA)
        // - Cannot be in the 900-999 range (900+ are reserved for ITINs or invalid)
        if ("000".equals(areaGroup) || "666".equals(areaGroup) || areaGroup.startsWith("9")) {
            return new ValidationResult(false, "Invalid SSN digits: Invalid Area Number segment");
        }

        // 3. Group Number Constraints (Middle 2 digits)
        // - Cannot be '00'
        if ("00".equals(groupGroup)) {
            return new ValidationResult(false, "Invalid SSN digits: Invalid Group Number segment");
        }

        // 4. Serial Number Constraints (Last 4 digits)
        // - Cannot be '0000'
        if ("0000".equals(serialGroup)) {
            return new ValidationResult(false, "Invalid SSN digits: Invalid Serial Number segment");
        }

        return new ValidationResult(true, null);
    }
}
