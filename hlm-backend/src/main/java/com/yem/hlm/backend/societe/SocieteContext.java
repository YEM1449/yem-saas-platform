package com.yem.hlm.backend.societe;

import java.util.UUID;

/**
 * SocieteContext — request context for the multi-société SaaS.
 *
 * Static ThreadLocal so it works in both HTTP requests (set by JwtAuthenticationFilter)
 * and @Scheduled methods (set via setSystem() to bypass société filtering).
 */
public class SocieteContext {

    private static final ThreadLocal<UUID>    SOCIETE_ID       = new ThreadLocal<>();
    private static final ThreadLocal<UUID>    USER_ID          = new ThreadLocal<>();
    private static final ThreadLocal<String>  ROLE             = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPER_ADMIN      = new ThreadLocal<>();
    /** Non-null when the current request uses an impersonation token (SA-7). */
    private static final ThreadLocal<UUID>    IMPERSONATED_BY  = new ThreadLocal<>();

    private SocieteContext() {}

    public static void setSocieteId(UUID societeId) { SOCIETE_ID.set(societeId); }
    public static UUID getSocieteId() { return SOCIETE_ID.get(); }

    public static void setUserId(UUID userId) { USER_ID.set(userId); }
    public static UUID getUserId() { return USER_ID.get(); }

    public static void setRole(String role) { ROLE.set(role); }
    public static String getRole() { return ROLE.get(); }

    public static void setSuperAdmin(boolean superAdmin) { SUPER_ADMIN.set(superAdmin); }
    public static boolean isSuperAdmin() { return Boolean.TRUE.equals(SUPER_ADMIN.get()); }

    /** Set when the current request carries an impersonation JWT (claim {@code imp}). */
    public static void setImpersonatedBy(UUID superAdminId) { IMPERSONATED_BY.set(superAdminId); }
    /** Returns the SUPER_ADMIN userId who initiated impersonation, or {@code null} for normal sessions. */
    public static UUID getImpersonatedBy() { return IMPERSONATED_BY.get(); }
    public static boolean isImpersonation() { return IMPERSONATED_BY.get() != null; }

    /**
     * System mode — used by @Scheduled methods to bypass société filtering.
     * Sets superAdmin=true and societeId=null so repositories don't filter by société.
     */
    public static void setSystem() {
        SOCIETE_ID.remove();
        SUPER_ADMIN.set(true);
    }

    public static void clear() {
        SOCIETE_ID.remove();
        USER_ID.remove();
        ROLE.remove();
        SUPER_ADMIN.remove();
        IMPERSONATED_BY.remove();
    }
}
