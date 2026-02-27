package com.yem.hlm.backend.deposit.service;

/**
 * Thrown when PDF generation fails due to an internal I/O error.
 * Maps to HTTP 500 in {@code GlobalExceptionHandler}.
 */
public class ReservationPdfGenerationException extends RuntimeException {

    public ReservationPdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
