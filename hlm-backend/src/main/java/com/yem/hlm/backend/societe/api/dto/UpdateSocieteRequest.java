package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * All fields are optional except {@code version} (used for optimistic-lock conflict detection).
 */
public record UpdateSocieteRequest(

        @NotNull Long version,

        // Core
        @Size(max = 255) String nom,
        @Size(max = 10)  String pays,

        // Legal identity
        @Size(max = 255) String nomCommercial,
        @Size(max = 50)  String formeJuridique,
        Long capitalSocial,
        @Size(max = 50)  String siretIce,
        @Size(max = 100) String rc,
        @Size(max = 50)  String ifNumber,
        @Size(max = 50)  String patente,
        @Size(max = 50)  String tvaNumber,
        @Size(max = 50)  String cnssNumber,

        // Location
        String adresseSiege,
        String adresse,
        @Size(max = 100) String ville,
        @Size(max = 20)  String codePostal,
        @Size(max = 100) String region,
        @Size(max = 30)  String telephone,
        @Size(max = 30)  String telephone2,
        @Email @Size(max = 255) String emailContact,
        @Size(max = 500) String siteWeb,
        @Size(max = 500) String linkedinUrl,

        // RGPD
        @Email @Size(max = 255) String emailDpo,
        @Size(max = 255) String dpoNom,
        @Size(max = 30)  String telephoneDpo,
        @Size(max = 100) String numeroCndp,
        @Size(max = 100) String numeroCnil,
        LocalDate dateDeclarationCndp,
        LocalDate dateDeclarationCnil,
        @Size(max = 50)  String baseJuridiqueDefaut,
        Integer dureeRetentionJours,

        // Real-estate licensing
        @Size(max = 100) String numeroAgrement,
        @Size(max = 100) String carteProfessionnelle,
        @Size(max = 200) String caisseGarantie,
        @Size(max = 200) String assuranceRc,
        LocalDate dateAgrement,
        LocalDate dateExpirationAgrement,
        @Size(max = 100) String typeActivite,
        String zonesIntervention,

        // Branding
        @Size(max = 500) String logoUrl,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex colour e.g. #3A5C8F")
        @Size(max = 7) String couleurPrimaire,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex colour e.g. #3A5C8F")
        @Size(max = 7) String couleurSecondaire,
        @Size(max = 10) String langueDefaut,
        @Size(max = 3)  String devise,
        @Size(max = 50) String fuseauHoraire,
        @Size(max = 20) String formatDate,
        String mentionsLegales,

        // Subscription (SUPER_ADMIN only)
        @Size(max = 50) String planAbonnement,
        Integer maxUtilisateurs,
        Integer maxBiens,
        Integer maxContacts,
        Integer maxProjets,
        LocalDate dateDebutAbonnement,
        LocalDate dateFinAbonnement,
        Boolean periodeEssai,

        // Admin notes (SUPER_ADMIN only)
        String notesInternes
) {}
