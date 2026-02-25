package com.yem.hlm.backend.project.api.dto;

import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        ProjectStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getTenant().getId(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
