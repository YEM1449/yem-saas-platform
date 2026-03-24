package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.*;

public record RetirerUtilisateurRequest(
    @NotBlank @Size(max = 500) String raison,
    @NotNull Long version
) {}
