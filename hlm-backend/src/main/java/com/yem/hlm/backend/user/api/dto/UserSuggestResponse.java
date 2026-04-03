package com.yem.hlm.backend.user.api.dto;

import java.util.UUID;

/**
 * Lightweight projection used by the typeahead suggest endpoint.
 * Exposed to ADMIN, MANAGER and AGENT so any CRM user can search
 * for colleagues when assigning tasks — without ever seeing UUIDs.
 */
public record UserSuggestResponse(
        UUID   id,
        String displayName,
        String email
) {}
