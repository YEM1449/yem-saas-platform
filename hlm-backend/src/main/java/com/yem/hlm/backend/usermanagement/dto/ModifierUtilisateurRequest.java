package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.*;

public record ModifierUtilisateurRequest(
    @Size(min = 2, max = 100) String prenom,
    @Size(min = 2, max = 100) String nomFamille,
    @Pattern(regexp = "^\\+?[0-9\\s\\-]{7,20}$") String telephone,
    @Size(max = 150) String poste,
    @Size(max = 10) String langueInterface,
    Boolean notificationsActives,
    String notesAdmin,
    @NotNull Long version
) {}
