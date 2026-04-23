package com.yem.hlm.backend.usermanagement.dto;

import java.util.List;
import java.util.UUID;

public record ProjectAccessResponse(
        UUID userId,
        /** Configured project IDs. Empty means unrestricted (access to all projects). */
        List<UUID> projectIds
) {}
