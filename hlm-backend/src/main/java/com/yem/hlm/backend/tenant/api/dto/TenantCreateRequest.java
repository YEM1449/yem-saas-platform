package com.yem.hlm.backend.tenant.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantCreateRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9-]+$")
        String key,
        @NotBlank
        @Size(max = 160)
        String name,
        @NotBlank
        @Email
        @Size(max = 160)
        String ownerEmail,
        @NotBlank
        @Size(min = 8, max = 72)
        String ownerPassword
) {
    public TenantCreateRequest {
        key = normalizeLower(key);
        name = normalizeTrim(name);
        ownerEmail = normalizeLower(ownerEmail);
    }

    private static String normalizeLower(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeTrim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}
