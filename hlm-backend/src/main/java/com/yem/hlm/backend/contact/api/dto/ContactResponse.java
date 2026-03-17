package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.ConsentMethod;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        ContactType contactType,
        ContactStatus status,
        boolean qualified,
        LocalDateTime tempClientUntil,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String email,
        String nationalId,
        String address,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // GDPR / Law 09-08 consent fields
        boolean consentGiven,
        Instant consentDate,
        ConsentMethod consentMethod,
        ProcessingBasis processingBasis
) {}
