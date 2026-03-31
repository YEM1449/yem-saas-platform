package com.yem.hlm.backend.property.service;

import java.util.UUID;

/**
 * Thrown when an immeuble assignment conflicts with the property's effective project.
 */
public class ImmeubleProjectMismatchException extends RuntimeException {

    public ImmeubleProjectMismatchException(UUID immeubleId, UUID projectId) {
        super("Immeuble " + immeubleId + " does not belong to project " + projectId);
    }
}
