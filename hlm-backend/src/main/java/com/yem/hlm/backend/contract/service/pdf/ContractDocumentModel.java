package com.yem.hlm.backend.contract.service.pdf;

/**
 * Flat DTO passed to the Thymeleaf contract template ({@code documents/contract.html}).
 * All date/time fields are pre-formatted strings (dd/MM/yyyy or dd/MM/yyyy HH:mm).
 * Nullable string fields are {@code null} when the underlying value is absent;
 * the template guards these with {@code th:if}.
 *
 * <p>Buyer fields prefer the immutable snapshot stored at signing time
 * ({@code SaleContract.buyerDisplayName} etc.) and fall back to the live
 * Contact fields for DRAFT contracts (snapshot not yet captured).
 */
public record ContractDocumentModel(

        // ── Tenant / Company ────────────────────────────────────────────────
        String tenantName,

        // ── Project / Property ──────────────────────────────────────────────
        String projectName,
        /** Property reference code. */
        String propertyRef,
        /** Property display title. */
        String propertyTitle,
        /** PropertyType enum name. */
        String propertyType,

        // ── Prices ──────────────────────────────────────────────────────────
        /** Negotiated agreed price (plain number + currency). */
        String agreedPrice,
        /** Original list price + currency, or null when not set. */
        String listPrice,

        // ── Buyer ────────────────────────────────────────────────────────────
        String buyerDisplayName,
        /** May be null when not on file. */
        String buyerPhone,
        /** May be null when not on file. */
        String buyerEmail,
        /** May be null when not on file. */
        String buyerAddress,
        /** ICE / national-ID, or null. */
        String buyerIce,
        /** "Personne physique" or "Personne morale". */
        String buyerType,

        // ── Agent ────────────────────────────────────────────────────────────
        String agentEmail,

        // ── Contract meta ────────────────────────────────────────────────────
        /** Short reference displayed on the PDF (first 8 chars of UUID, uppercase). */
        String contractReference,
        /** SaleContractStatus enum name. */
        String contractStatus,
        /** dd/MM/yyyy HH:mm, or null when not yet signed. */
        String signedAt,
        /** dd/MM/yyyy HH:mm, or null when not canceled. */
        String canceledAt,
        /** dd/MM/yyyy — creation date. */
        String createdAt,

        // ── Document meta ────────────────────────────────────────────────────
        /** dd/MM/yyyy HH:mm at generation time. */
        String generatedAt
) {}
