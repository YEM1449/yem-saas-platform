package com.yem.hlm.backend.gdpr.service;

import java.util.List;
import java.util.UUID;

/**
 * Thrown when a GDPR erasure request cannot be fulfilled because the contact
 * has one or more active SIGNED contracts. Legal archive obligations prevent full anonymization.
 */
public class GdprErasureBlockedException extends RuntimeException {

    private final List<UUID> blockingContractIds;

    public GdprErasureBlockedException(List<UUID> blockingContractIds) {
        super("Contact cannot be anonymized: active SIGNED contracts exist: " + blockingContractIds);
        this.blockingContractIds = blockingContractIds;
    }

    public List<UUID> getBlockingContractIds() {
        return blockingContractIds;
    }
}
