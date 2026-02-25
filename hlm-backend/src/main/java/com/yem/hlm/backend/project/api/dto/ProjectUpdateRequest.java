package com.yem.hlm.backend.project.api.dto;

import com.yem.hlm.backend.project.domain.ProjectStatus;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        ProjectStatus status
) {
    public ProjectUpdateRequest {
        if (name != null) name = name.trim();
    }
}
