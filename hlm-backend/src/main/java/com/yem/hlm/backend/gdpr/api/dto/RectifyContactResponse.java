package com.yem.hlm.backend.gdpr.api.dto;

import com.yem.hlm.backend.contact.domain.ConsentMethod;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;

import java.util.UUID;

/**
 * Current mutable personal fields for the GDPR rectification workflow (Art. 16).
 * <p>
 * This is a read-only snapshot. To perform rectification use the existing
 * {@code PATCH /api/contacts/{id}} endpoint.
 */
public record RectifyContactResponse(
        UUID contactId,
        String fullName,
        String email,
        String phone,
        boolean consentGiven,
        ConsentMethod consentMethod,
        ProcessingBasis processingBasis
) {}
