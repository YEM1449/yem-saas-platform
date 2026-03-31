package com.yem.hlm.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that at least one of phone or email is non-blank.
 * Works on any object that has {@code phone()} and {@code email()} accessor methods
 * by delegating to a simple interface check. Since Java records are sealed,
 * we use reflection-free duck typing via an explicit interface.
 */
public class PhoneOrEmailRequiredValidator implements ConstraintValidator<PhoneOrEmailRequired, PhoneOrEmailTarget> {

    @Override
    public boolean isValid(PhoneOrEmailTarget target, ConstraintValidatorContext ctx) {
        if (target == null) return true; // null objects handled by @NotNull if needed
        boolean hasPhone = target.phone() != null && !target.phone().isBlank();
        boolean hasEmail = target.email() != null && !target.email().isBlank();
        return hasPhone || hasEmail;
    }
}
