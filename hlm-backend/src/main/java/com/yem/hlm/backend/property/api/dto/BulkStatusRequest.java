package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/properties/bulk-status}.
 * Applies a single editorial status to a list of property IDs.
 * Properties that are RESERVED or SOLD are skipped (not rejected).
 */
public record BulkStatusRequest(
        @NotEmpty List<@NotNull UUID> ids,
        @NotNull PropertyStatus status
) {}
