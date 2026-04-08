package com.yem.hlm.backend.tranche.api.dto;

import com.yem.hlm.backend.tranche.domain.TrancheStatut;
import jakarta.validation.constraints.NotNull;

public record UpdateTrancheStatutRequest(
        @NotNull TrancheStatut statut
) {}
