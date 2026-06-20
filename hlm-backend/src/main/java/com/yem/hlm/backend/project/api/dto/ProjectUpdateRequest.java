package com.yem.hlm.backend.project.api.dto;

import com.yem.hlm.backend.project.domain.ProjectStatus;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 2000) String description,
        ProjectStatus status,
        // Optimistic-lock version the client read from GET (EX-003). Required for stale-form
        // protection: a mismatch (incl. null) → 409 CONCURRENT_UPDATE instead of overwriting a
        // concurrent edit. Mirrors the user/société update flows.
        Long version
) {
    public ProjectUpdateRequest {
        if (name != null) name = name.trim();
    }
}
