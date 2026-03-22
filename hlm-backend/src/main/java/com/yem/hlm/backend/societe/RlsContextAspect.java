package com.yem.hlm.backend.societe;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets the PostgreSQL session variable {@code app.current_societe_id} before each
 * {@code @Transactional} method executes. Used by Row-Level Security (RLS) policies
 * on {@code contact} and {@code property} tables as a defense-in-depth layer.
 *
 * <p>{@code SET LOCAL} is scoped to the current transaction — it auto-resets when
 * the transaction commits or rolls back, so no cleanup is needed.
 *
 * <p>When the context has no société (system/scheduler mode), the variable is set to
 * the nil UUID ({@code 00000000-…}) which matches no RLS policy row — schedulers that
 * need cross-société access pass societeId explicitly as query parameters.
 */
@Aspect
@Component
public class RlsContextAspect {

    private static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    private final JdbcTemplate jdbc;

    public RlsContextAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void setSocieteIdOnConnection() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId != null) {
            jdbc.execute("SET LOCAL app.current_societe_id = '" + societeId + "'");
        } else {
            // System mode (scheduler) or SUPER_ADMIN: set nil UUID so RLS blocks all rows
            // by default; schedulers must pass societeId parameters explicitly.
            jdbc.execute("SET LOCAL app.current_societe_id = '" + NIL_UUID + "'");
        }
    }
}
