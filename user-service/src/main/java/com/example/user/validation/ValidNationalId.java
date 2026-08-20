package com.example.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NationalIdValidator.class)
public @interface ValidNationalId {

    String message() default "National ID is invalid for the selected country";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
