package com.yem.hlm.backend.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description
) {
    public ProjectCreateRequest {
        if (name != null) name = name.trim();
    }
}
