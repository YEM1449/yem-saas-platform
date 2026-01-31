package com.yem.hlm.backend.tenant.api.dto;

import java.util.UUID;

public record TenantResponse(TenantInfo tenant, OwnerResponse owner) {

    public record TenantInfo(UUID id, String key, String name) {
    }
}
