package com.yem.hlm.backend.societe;

/**
 * Thrown when a société-scoped (CRM-role) principal reaches a {@code @Transactional} path with no
 * société in context (P5 — defense-in-depth tenant isolation).
 *
 * <p>The hand-rolled {@code requireSocieteId()} checks plus RLS already isolate normal traffic;
 * this is the fail-closed backstop for the one dangerous gap: if an authenticated ADMIN/MANAGER/AGENT
 * ever reaches the DB layer without a société, {@link RlsContextAspect} must <b>not</b> fall through
 * to the nil-UUID RLS <em>bypass</em> (which exposes every tenant). Instead it throws this, turning a
 * latent cross-tenant read into a hard 403. It should never fire in normal operation — if it does,
 * it is a bug (a forgotten/cleared context) or an attack, and is logged + counted for alerting (P4).
 */
public class TenantIsolationException extends RuntimeException {
    public TenantIsolationException(String message) {
        super(message);
    }
}
