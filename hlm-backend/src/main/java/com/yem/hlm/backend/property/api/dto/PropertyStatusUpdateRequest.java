package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for the editorial status transition endpoint.
 *
 * <p>Only non-commercial statuses (DRAFT, ACTIVE, WITHDRAWN, ARCHIVED) may be
 * set via this endpoint. RESERVED and SOLD are exclusively controlled by the
 * commercial workflow (reservation/deposit/contract services) and are rejected.
 */
public record PropertyStatusUpdateRequest(
        @NotNull PropertyStatus status
) {}
