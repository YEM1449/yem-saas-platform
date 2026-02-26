package com.yem.hlm.backend.contract.service;

import java.util.UUID;

public class ContractNotFoundException extends RuntimeException {
    public ContractNotFoundException(UUID contractId) {
        super("Contract not found: " + contractId);
    }
}
