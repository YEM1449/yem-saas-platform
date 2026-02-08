package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.InterestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ContactInterestResponse(
        UUID propertyId,
        InterestStatus interestStatus,
        LocalDateTime createdAt
) {}
