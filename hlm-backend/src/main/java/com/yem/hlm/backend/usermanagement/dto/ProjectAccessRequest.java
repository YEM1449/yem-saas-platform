package com.yem.hlm.backend.usermanagement.dto;

import java.util.List;
import java.util.UUID;

public record ProjectAccessRequest(
        /** List of project IDs this user may access. Empty list = full access (no restriction). */
        List<UUID> projectIds
) {}
