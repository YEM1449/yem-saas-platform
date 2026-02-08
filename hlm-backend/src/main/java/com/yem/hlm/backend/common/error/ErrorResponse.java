package com.yem.hlm.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard error response contract for all API errors.
 * Provides stable JSON structure for frontend error handling.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
) {
    /**
     * Create error response without field errors.
     */
    public static ErrorResponse of(
            int status,
            String error,
            ErrorCode code,
            String message,
            String path
    ) {
        return new ErrorResponse(
                OffsetDateTime.now().toString(),
                status,
                error,
                code,
                message,
                path,
                null,
                null
        );
    }

    /**
     * Create error response with field errors (for validation).
     */
    public static ErrorResponse withFieldErrors(
            int status,
            String error,
            ErrorCode code,
            String message,
            String path,
            List<FieldError> fieldErrors
    ) {
        return new ErrorResponse(
                OffsetDateTime.now().toString(),
                status,
                error,
                code,
                message,
                path,
                null,
                fieldErrors
        );
    }
}
