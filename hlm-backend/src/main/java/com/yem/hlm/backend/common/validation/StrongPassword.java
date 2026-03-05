package com.yem.hlm.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a password meets complexity requirements:
 * minimum 12 characters, at least one uppercase letter, one lowercase letter,
 * one digit, and one special character.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
