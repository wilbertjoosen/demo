package com.example.user.validation;

import com.example.user.dto.ValidationResult;
import com.example.user.validation.nationalid.NationalIdStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NationalIdValidationService {

    private final Map<String, NationalIdStrategy> strategies;

    public NationalIdValidationService(
            List<NationalIdStrategy> strategyList) {

        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NationalIdStrategy::countryCode,
                        Function.identity()
                ));
    }

    public ValidationResult isValid(
            String country,
            String nationalId) {

        if (country == null || country.isBlank()) {
            return new ValidationResult(true, null);
        }

        NationalIdStrategy strategy = strategies.get(country.toUpperCase());

        // Option B style: if country isn't explicitly supported with a validator, accept it
        if (strategy == null) {
            return new ValidationResult(true, null);
        }

        return strategy.isValid(nationalId);
    }
}
