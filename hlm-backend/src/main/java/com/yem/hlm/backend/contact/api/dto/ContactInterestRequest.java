package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.InterestStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ContactInterestRequest(
        @NotNull
        UUID propertyId,
        InterestStatus interestStatus
) {}
