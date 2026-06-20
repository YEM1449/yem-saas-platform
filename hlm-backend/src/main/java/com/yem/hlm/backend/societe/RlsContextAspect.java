package com.yem.hlm.backend.societe;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets the PostgreSQL session variable {@code app.current_societe_id} before each
 * {@code @Transactional} method executes. Used by Row-Level Security (RLS) policies
 * on all domain tables as a defense-in-depth isolation layer.
 *
 * <h3>How it works</h3>
 * <p>Runs inside the active transaction via {@link JdbcTemplate} which participates in
 * the same connection as Hibernate when {@code TransactionSynchronizationManager} is active.
 * Uses PostgreSQL {@code set_config('name', value, is_local=true)} so the variable
 * is automatically cleared when the transaction ends.
 *
 * <h3>System/scheduler mode</h3>
 * <p>When {@code SocieteContext} has no société (scheduler, SUPER_ADMIN, Liquibase),
 * the variable is set to the nil UUID ({@code 00000000-…}). The RLS policies are
 * written to treat the nil UUID as a <em>bypass</em> sentinel — all rows are visible
 * to system-mode connections so that cross-société schedulers (outbox, reminders) work
 * correctly while still using explicit {@code societe_id} parameters in JPA queries
 * as the primary isolation mechanism.
 *
 * <h3>Pointcut</h3>
 * <p>Captures both method-level and class-level {@code @Transactional} annotations via
 * {@code @within || @annotation} so service classes like {@code InvitationService}
 * (which annotate the class, not each method) are correctly intercepted.
 *
 * <h3>Ordering</h3>
 * <p>{@code @Order(Ordered.LOWEST_PRECEDENCE - 1)} ensures this advice runs slightly
 * inside the default transaction interceptor order, so {@code JdbcTemplate.execute()}
 * participates in the already-opened transaction connection.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class RlsContextAspect {

    /** Nil UUID used as system/bypass sentinel in RLS policies. */
    public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    private final JdbcTemplate jdbc;

    public RlsContextAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sets {@code app.current_societe_id} on the current transaction's connection.
     *
     * <p>Pointcut covers:
     * <ul>
     *   <li>{@code @annotation} — methods annotated directly with {@code @Transactional}</li>
     *   <li>{@code @within} — methods in classes annotated with {@code @Transactional}</li>
     * </ul>
     */
    @Before("@within(org.springframework.transaction.annotation.Transactional) "
          + "|| @annotation(org.springframework.transaction.annotation.Transactional)")
    public void setSocieteIdOnConnection() {
        UUID societeId = SocieteContext.getSocieteId();

        // P5 — fail closed. A null société means the nil-UUID *bypass* below (all rows visible).
        // That is correct for system/scheduler/SUPER_ADMIN mode (isSuperAdmin()==true) and for
        // unauthenticated/pre-auth flows (login, portal magic-link) which carry no role and rely on
        // JPQL societe_id params. But an authenticated CRM principal (ADMIN/MANAGER/AGENT) must NEVER
        // reach the DB without a société — the JwtAuthenticationFilter guarantees one, so if it is
        // missing here the context was lost/forgotten and bypassing would be a cross-tenant IDOR.
        // Turn that into a hard failure instead.
        if (societeId == null && !SocieteContext.isSuperAdmin() && isCrmTenantPrincipal()) {
            throw new TenantIsolationException(
                    "Société-scoped principal reached a transactional path without a société context "
                    + "(role=" + SocieteContext.getRole() + ", user=" + SocieteContext.getUserId() + ")");
        }

        String sid = (societeId != null) ? societeId.toString() : NIL_UUID;
        // set_config(name, value, is_local=true) — scoped to current transaction.
        // Uses parameterised form to prevent any injection risk.
        jdbc.queryForObject(
                "SELECT set_config('app.current_societe_id', ?, true)",
                String.class,
                sid);
    }

    /** True when the current principal is a tenant CRM role (ADMIN/MANAGER/AGENT) — i.e. must be société-scoped. */
    private boolean isCrmTenantPrincipal() {
        String role = SocieteContext.getRole();
        return "ROLE_ADMIN".equals(role) || "ROLE_MANAGER".equals(role) || "ROLE_AGENT".equals(role);
    }
}
