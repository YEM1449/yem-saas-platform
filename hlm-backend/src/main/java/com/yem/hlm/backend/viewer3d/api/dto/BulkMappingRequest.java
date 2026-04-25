package com.yem.hlm.backend.viewer3d.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkMappingRequest(
        @NotNull @Size(max = 2000) @Valid List<MappingEntry> mappings
) {
    public record MappingEntry(
            @NotNull UUID      propertyId,
            @NotNull @Size(max = 255) String meshId,
            @Size(max = 255) String immeubleMeshId,
            @Size(max = 255) String trancheMeshId
    ) {}
}
