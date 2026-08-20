package com.example.user.validation.nationalid;

import com.example.user.dto.ValidationResult;

public interface NationalIdStrategy {

    String countryCode();

    ValidationResult isValid(String nationalId);
}
