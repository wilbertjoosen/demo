package com.example.user.validation;

import com.example.user.dto.ValidationResult;
import com.example.user.dto.request.UpdateProfileRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NationalIdValidator implements ConstraintValidator<ValidNationalId, UpdateProfileRequest> {

    private final NationalIdValidationService validationService;

    @Override
    public boolean isValid(UpdateProfileRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        String country = request.nationalIdCountry();
        String nationalId = request.nationalId();

        if (country == null || country.isBlank() || nationalId == null || nationalId.isBlank()) {
            return true; // Let @NotBlank handle missing fields if required
        }

        ValidationResult result = validationService.isValid(country, nationalId);

        if (!result.valid()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(result.message())
                    .addPropertyNode("nationalId")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
