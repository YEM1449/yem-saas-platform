package com.yem.hlm.backend.contract.service;

/**
 * Thrown when the provided {@code sourceDepositId} does not match the contract's
 * property / buyer / agent, or is not in CONFIRMED status.
 * Maps to HTTP 400 Bad Request.
 */
public class ContractDepositMismatchException extends RuntimeException {
    public ContractDepositMismatchException(String reason) {
        super("Deposit mismatch: " + reason);
    }
}
