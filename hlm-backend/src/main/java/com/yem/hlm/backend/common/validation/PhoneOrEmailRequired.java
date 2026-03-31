package com.yem.hlm.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint that ensures at least one of phone or email is provided.
 */
@Documented
@Constraint(validatedBy = PhoneOrEmailRequiredValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneOrEmailRequired {
    String message() default "Phone number or email is required";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
