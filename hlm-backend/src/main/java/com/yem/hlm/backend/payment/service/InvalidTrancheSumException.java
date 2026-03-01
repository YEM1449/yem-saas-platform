package com.yem.hlm.backend.payment.service;

/** Thrown when tranche percentages or amounts do not sum to the required total. */
public class InvalidTrancheSumException extends RuntimeException {
    public InvalidTrancheSumException(String message) {
        super(message);
    }
}
