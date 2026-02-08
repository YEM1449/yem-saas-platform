package com.yem.hlm.backend.user.domain;

/**
 * Defines the roles available in the CRM-HLM system for role-based access control (RBAC).
 * <p>
 * Role hierarchy (from highest to lowest privileges):
 * <ul>
 *   <li><b>ROLE_ADMIN</b>: Full system access, can manage all resources including deletion</li>
 *   <li><b>ROLE_MANAGER</b>: Can create, read, and update resources (no deletion)</li>
 *   <li><b>ROLE_AGENT</b>: Read-only access to properties and contacts (default for new users)</li>
 * </ul>
 * <p>
 * Note: Spring Security requires role names to be prefixed with "ROLE_" for {@code hasRole()} expressions.
 * When using {@code @PreAuthorize("hasRole('ADMIN')")}, Spring automatically adds the ROLE_ prefix.
 */
public enum UserRole {
    /**
     * Administrator with full CRUD access.
     */
    ROLE_ADMIN,

    /**
     * Manager can create, read, update resources (no delete).
     */
    ROLE_MANAGER,

    /**
     * Agent has read-only access (default for new users).
     */
    ROLE_AGENT
}
