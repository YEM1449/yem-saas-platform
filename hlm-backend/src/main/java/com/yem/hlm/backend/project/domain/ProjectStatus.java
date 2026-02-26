package com.yem.hlm.backend.project.domain;

/**
 * Status of a real-estate project.
 * ACTIVE  — project is ongoing and can receive new properties.
 * ARCHIVED — project is closed; existing properties remain linked but no new ones may be added.
 */
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED
}
