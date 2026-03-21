package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.*;

public record InviterUtilisateurRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 2, max = 100) String prenom,
    @NotBlank @Size(min = 2, max = 100) String nomFamille,
    @Pattern(regexp = "^\\+?[0-9\\s\\-]{7,20}$", message = "Format téléphone invalide") String telephone,
    @Size(max = 150) String poste,
    @NotNull @Pattern(regexp = "ADMIN|MANAGER|AGENT", message = "Rôle invalide") String role,
    @Size(max = 10) String langueInterface,
    @Size(max = 500) String messagePersonnalise
) {}
