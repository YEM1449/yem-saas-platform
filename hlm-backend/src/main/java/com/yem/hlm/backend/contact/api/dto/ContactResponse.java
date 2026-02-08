package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;

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
        LocalDateTime updatedAt
) {}
