package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.Societe;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public-safe company view — no {@code notesInternes} (R9).
 */
public record SocieteDto(
        UUID    id,
        String  key,
        String  nom,
        String  nomCommercial,
        String  formeJuridique,
        String  siretIce,
        String  pays,
        boolean actif,
        String  planAbonnement,
        boolean periodeEssai,
        LocalDate dateDebutAbonnement,
        LocalDate dateFinAbonnement,

        // Location
        String adresse,
        String adresseSiege,
        String ville,
        String codePostal,
        String region,
        String telephone,
        String emailContact,
        String siteWeb,
        String logoUrl,
        String couleurPrimaire,
        String couleurSecondaire,
        String langueDefaut,
        String devise,

        // RGPD — public fields only
        String emailDpo,
        String dpoNom,
        String baseJuridiqueDefaut,

        // Licensing
        String numeroAgrement,
        LocalDate dateExpirationAgrement,
        String typeActivite,

        int     complianceScore,
        Long    version,
        Instant createdAt,
        Instant updatedAt,
        String  logoDownloadUrl
) {
    public static SocieteDto from(Societe s) {
        String logoDownloadUrl = s.getLogoFileKey() != null
                ? "/api/societes/" + s.getId() + "/logo"
                : null;
        return new SocieteDto(
                s.getId(),
                s.getKey(),
                s.getNom(),
                s.getNomCommercial(),
                s.getFormeJuridique(),
                s.getSiretIce(),
                s.getPays(),
                s.isActif(),
                s.getPlanAbonnement(),
                s.isPeriodeEssai(),
                s.getDateDebutAbonnement(),
                s.getDateFinAbonnement(),
                s.getAdresse(),
                s.getAdresseSiege(),
                s.getVille(),
                s.getCodePostal(),
                s.getRegion(),
                s.getTelephone(),
                s.getEmailContact(),
                s.getSiteWeb(),
                s.getLogoUrl(),
                s.getCouleurPrimaire(),
                s.getCouleurSecondaire(),
                s.getLangueDefaut(),
                s.getDevise(),
                s.getEmailDpo(),
                s.getDpoNom(),
                s.getBaseJuridiqueDefaut(),
                s.getNumeroAgrement(),
                s.getDateExpirationAgrement(),
                s.getTypeActivite(),
                s.getComplianceScore(),
                s.getVersion(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                logoDownloadUrl
        );
    }
}
