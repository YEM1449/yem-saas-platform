package com.yem.hlm.backend.tenant.api.dto;

import java.util.UUID;

public record OwnerResponse(UUID id, String email, boolean enabled) {
}
