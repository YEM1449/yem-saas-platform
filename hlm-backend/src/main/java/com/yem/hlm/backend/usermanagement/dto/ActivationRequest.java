package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.*;

public record ActivationRequest(
    @NotBlank @Size(min = 12, max = 128) String motDePasse,
    @NotBlank String confirmationMotDePasse,
    @AssertTrue(message = "Vous devez accepter les CGU") boolean consentementCgu,
    @NotBlank String consentementCguVersion
) {
    @AssertTrue(message = "Les mots de passe ne correspondent pas")
    public boolean isMotDePasseConfirme() {
        return motDePasse != null && motDePasse.equals(confirmationMotDePasse);
    }
}
