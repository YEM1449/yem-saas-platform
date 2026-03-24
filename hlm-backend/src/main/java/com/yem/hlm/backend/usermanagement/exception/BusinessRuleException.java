package com.yem.hlm.backend.usermanagement.exception;

import com.yem.hlm.backend.common.error.ErrorCode;

/**
 * Thrown when a business rule is violated in the user management module.
 * The error code drives the HTTP status returned by GlobalExceptionHandler.
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
