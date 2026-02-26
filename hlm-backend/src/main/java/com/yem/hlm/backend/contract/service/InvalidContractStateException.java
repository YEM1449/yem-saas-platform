package com.yem.hlm.backend.contract.service;

/**
 * Thrown when a contract lifecycle action is attempted on a contract that is
 * not in the required state (e.g. signing an already SIGNED or CANCELED contract).
 * Maps to HTTP 409 Conflict.
 */
public class InvalidContractStateException extends RuntimeException {
    public InvalidContractStateException(String message) {
        super(message);
    }
}
