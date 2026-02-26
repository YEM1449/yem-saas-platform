package com.yem.hlm.backend.contract.domain;

/**
 * Lifecycle states for a {@link SaleContract}.
 * <p>
 * Allowed transitions:
 * <pre>
 *   DRAFT ──sign──► SIGNED
 *   DRAFT ──cancel──► CANCELED
 *   SIGNED ──cancel──► CANCELED   (business rescission — [OPEN POINT]: requires ADMIN/MANAGER)
 *   CANCELED is terminal.
 * </pre>
 */
public enum SaleContractStatus {
    DRAFT,
    SIGNED,
    CANCELED
}
