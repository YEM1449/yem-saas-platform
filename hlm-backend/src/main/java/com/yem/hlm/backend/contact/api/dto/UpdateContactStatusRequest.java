package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.ContactStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateContactStatusRequest(
        @NotNull ContactStatus status
) {}
