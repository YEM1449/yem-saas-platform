package com.yem.hlm.backend.payments.service;

import java.math.BigDecimal;

/**
 * Flat DTO passed to the Thymeleaf call-for-funds template.
 * All date fields are pre-formatted strings (dd/MM/yyyy) to keep templates
 * free of formatting logic. Nullable fields use {@code null}; the template
 * guards with {@code th:if}.
 */
public record CallForFundsDocumentModel(

        // ── Company / Tenant ────────────────────────────────────────────────
        String tenantName,

        // ── Property / Project ──────────────────────────────────────────────
        String projectName,
        String propertyRef,
        String propertyTitle,

        // ── Buyer ───────────────────────────────────────────────────────────
        String buyerFullName,
        String buyerPhone,
        String buyerEmail,
        String buyerAddress,

        // ── Contract ────────────────────────────────────────────────────────
        /** Agreed sale price, plain string. */
        String agreedPrice,

        // ── Schedule item ───────────────────────────────────────────────────
        int itemSequence,
        String itemLabel,
        /** Amount due for this installment, plain string. */
        BigDecimal itemAmount,
        /** Due date — dd/MM/yyyy. */
        String dueDate,
        String itemNotes,
        /** Status enum name. */
        String itemStatus,

        // ── Remaining ───────────────────────────────────────────────────────
        BigDecimal amountPaid,
        BigDecimal amountRemaining,

        // ── Agent ───────────────────────────────────────────────────────────
        String agentEmail,

        // ── Document meta ───────────────────────────────────────────────────
        /** dd/MM/yyyy HH:mm at generation time. */
        String generatedAt
) {}
