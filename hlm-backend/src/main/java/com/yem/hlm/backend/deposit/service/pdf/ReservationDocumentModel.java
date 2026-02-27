package com.yem.hlm.backend.deposit.service.pdf;

/**
 * Flat DTO passed to the Thymeleaf reservation template.
 * All date/time fields are pre-formatted strings (dd/MM/yyyy or dd/MM/yyyy HH:mm)
 * to keep the template free of formatting logic.
 * Nullable string fields are {@code null} when the underlying value is absent;
 * the template guards these with {@code th:if}.
 *
 * <h3>Where to change the PDF layout</h3>
 * <ul>
 *   <li>Data fields: change the builder in {@link ReservationDocumentService}.</li>
 *   <li>Labels / wording: edit {@code templates/documents/reservation.html}.</li>
 *   <li>Visual design (colours, fonts, spacing): edit the {@code <style>} block
 *       inside the same template.</li>
 * </ul>
 */
public record ReservationDocumentModel(

        // ── Tenant / Company ────────────────────────────────────────────────
        String tenantName,

        // ── Property ────────────────────────────────────────────────────────
        /** Project name, or "—" if no project. */
        String projectName,
        /** Property reference code, or "—". */
        String propertyRef,
        /** Property display title. */
        String propertyTitle,
        /** PropertyType enum name. */
        String propertyType,
        /** Formatted price + currency, or null when price not set. */
        String propertyPrice,

        // ── Buyer (Contact) ──────────────────────────────────────────────────
        String buyerFullName,
        /** May be null when not on file. */
        String buyerPhone,
        /** May be null when not on file. */
        String buyerEmail,

        // ── Deposit / Reservation ─────────────────────────────────────────────
        String depositReference,
        /** DepositStatus enum name. */
        String depositStatus,
        /** Formatted amount + currency. */
        String depositAmount,
        /** dd/MM/yyyy */
        String depositDate,
        /** dd/MM/yyyy HH:mm, or null. */
        String dueDate,
        /** dd/MM/yyyy HH:mm, or null. */
        String confirmedAt,
        /** dd/MM/yyyy HH:mm, or null. */
        String cancelledAt,
        /** Free-text notes, or null when blank. */
        String notes,

        // ── Agent ─────────────────────────────────────────────────────────────
        String agentEmail,

        // ── Document meta ─────────────────────────────────────────────────────
        /** dd/MM/yyyy HH:mm at generation time. */
        String generatedAt
) {}
