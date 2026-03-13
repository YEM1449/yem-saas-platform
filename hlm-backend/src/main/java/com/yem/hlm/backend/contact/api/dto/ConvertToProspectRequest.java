package com.yem.hlm.backend.contact.api.dto;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/contacts/{id}/convert-to-prospect}.
 * All fields are optional — they enrich the ProspectDetail record if provided.
 */
public record ConvertToProspectRequest(
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        /** Lead source (e.g. "WEBSITE", "REFERRAL", "SOCIAL_MEDIA"). */
        String source,
        String notes
) {}
