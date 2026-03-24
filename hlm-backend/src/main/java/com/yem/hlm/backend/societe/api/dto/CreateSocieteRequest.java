package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSocieteRequest(

        // Core — required
        @NotBlank @Size(max = 255) String nom,
        @NotBlank @Size(max = 10)  String pays,

        // Legal identity
        @Size(max = 255) String nomCommercial,
        @Size(max = 50)  String formeJuridique,
        @Size(max = 50)  String siretIce,
        @Size(max = 100) String rc,
        @Size(max = 50)  String ifNumber,
        @Size(max = 50)  String patente,
        @Size(max = 50)  String tvaNumber,
        @Size(max = 50)  String cnssNumber,

        // Location
        String adresseSiege,
        @Size(max = 100) String ville,
        @Size(max = 20)  String codePostal,
        @Size(max = 100) String region,
        @Size(max = 30)  String telephone,
        @Size(max = 30)  String telephone2,
        @Email @Size(max = 255) String emailContact,
        @Size(max = 500) String siteWeb,

        // RGPD
        @Email @Size(max = 255) String emailDpo,
        @Size(max = 255) String dpoNom,
        @Size(max = 30)  String telephoneDpo,
        @Size(max = 50)  String baseJuridiqueDefaut,

        // Real-estate licensing
        @Size(max = 100) String numeroAgrement,
        @Size(max = 100) String typeActivite,

        // Branding
        @Size(max = 500) String logoUrl,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex colour e.g. #3A5C8F")
        @Size(max = 7) String couleurPrimaire,
        @Size(max = 10) String langueDefaut,
        @Size(max = 3)  String devise,

        // Subscription
        @Size(max = 50) String planAbonnement,

        // Admin notes (SUPER_ADMIN only — not exposed in RGPD exports)
        String notesInternes
) {}
