package com.yem.hlm.backend.contact.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/contacts/{id}/convert-to-prospect}.
 * All fields are optional — they enrich the ProspectDetail record if provided.
 */
public record ConvertToProspectRequest(
        @DecimalMin("0.00") BigDecimal budgetMin,
        @DecimalMin("0.00") BigDecimal budgetMax,
        /** Lead source (e.g. "WEBSITE", "REFERRAL", "SOCIAL_MEDIA"). */
        @Size(max = 100) String source,
        @Size(max = 5000) String notes
) {}
