package com.yem.hlm.backend.societe;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Centralized helper for accessing the current société context.
 * Inject this instead of calling SocieteContext static methods directly.
 * Provides null-safe accessors that throw CrossSocieteAccessException
 * if the required context is missing.
 */
@Component
public class SocieteContextHelper {

    /**
     * Returns the current société ID or throws if missing.
     * Use this in every service/controller method that operates on société-scoped data.
     */
    public UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) {
            throw new CrossSocieteAccessException("Missing société context");
        }
        return societeId;
    }

    /**
     * Returns the current user ID or throws if missing.
     */
    public UUID requireUserId() {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) {
            throw new CrossSocieteAccessException("Missing user context");
        }
        return userId;
    }

    /**
     * Returns the current role from the société context.
     */
    public String getRole() {
        return SocieteContext.getRole();
    }

    /**
     * Returns true if the current context is a SUPER_ADMIN.
     */
    public boolean isSuperAdmin() {
        return SocieteContext.isSuperAdmin();
    }

    /**
     * Executes a task in system mode (no société scope, superAdmin=true).
     * Used by schedulers that operate across all sociétés.
     * Clears context in finally block to prevent ThreadLocal leaks.
     */
    public void runAsSystem(Runnable task) {
        try {
            SocieteContext.setSystem();
            task.run();
        } finally {
            SocieteContext.clear();
        }
    }
}
