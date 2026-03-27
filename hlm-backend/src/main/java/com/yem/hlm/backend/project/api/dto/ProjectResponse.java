package com.yem.hlm.backend.project.api.dto;

import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID societeId,
        String name,
        String description,
        ProjectStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String logoUrl
) {
    public static ProjectResponse from(Project p) {
        String logoUrl = p.getLogoFileKey() != null
                ? "/api/projects/" + p.getId() + "/logo"
                : null;
        return new ProjectResponse(
                p.getId(),
                p.getSocieteId(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                logoUrl
        );
    }
}
