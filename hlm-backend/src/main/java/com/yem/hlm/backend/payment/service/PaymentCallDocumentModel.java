package com.yem.hlm.backend.payment.service;

/**
 * Flat DTO passed to the Thymeleaf "Appel de Fonds" template
 * ({@code documents/appel-de-fonds.html}).
 *
 * <p>All date/time fields are pre-formatted strings (dd/MM/yyyy).
 * Nullable fields are {@code null} when the underlying value is absent;
 * the template guards these with {@code th:if}.
 */
public record PaymentCallDocumentModel(

        // ── Tenant ──────────────────────────────────────────────────────────
        String tenantName,

        // ── Property / Project ───────────────────────────────────────────────
        String projectName,
        String propertyRef,
        String propertyTitle,

        // ── Buyer (from contract snapshot) ───────────────────────────────────
        String buyerDisplayName,
        /** May be null. */
        String buyerPhone,
        /** May be null. */
        String buyerEmail,
        /** May be null. */
        String buyerAddress,

        // ── Contract ─────────────────────────────────────────────────────────
        String contractReference,
        String agreedPrice,

        // ── Tranche / Call ───────────────────────────────────────────────────
        String trancheLabel,
        String tranchePercentage,
        String trancheAmount,
        /** May be null when trigger-based. */
        String trancheDueDate,
        /** May be null. */
        String triggerCondition,
        int    callNumber,
        String amountDue,
        /** Call issue date (dd/MM/yyyy). */
        String issuedAt,
        /** Call status enum name. */
        String callStatus,

        // ── Agent ────────────────────────────────────────────────────────────
        String agentEmail,

        // ── Document meta ────────────────────────────────────────────────────
        String generatedAt
) {}
