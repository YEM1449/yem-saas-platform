package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSocieteRequest(
        @NotBlank @Size(max = 255) String nom,
        String siretIce,
        String adresse,
        @Size(max = 255) String emailDpo,
        @Size(max = 500) String logoUrl,
        @Size(max = 10) String pays
) {}
