package com.yem.hlm.backend.project.service;

import java.util.UUID;

/**
 * Thrown when a property attempts to be created or reassigned to an ARCHIVED project.
 * Only ACTIVE projects may accept new or updated property assignments.
 */
public class ArchivedProjectAssignmentException extends RuntimeException {

    public ArchivedProjectAssignmentException(UUID projectId) {
        super("Project " + projectId + " is archived and cannot accept new property assignments");
    }
}
