package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.Societe;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full company view for SUPER_ADMIN — includes internal fields such as {@code notesInternes}.
 * Never returned to regular users.
 */
public record SocieteDetailDto(
        UUID    id,
        String  key,
        String  nom,
        String  nomCommercial,
        String  formeJuridique,
        Long    capitalSocial,
        String  siretIce,
        String  rc,
        String  ifNumber,
        String  patente,
        String  tvaNumber,
        String  cnssNumber,
        String  pays,
        boolean actif,

        // Location
        String adresse,
        String adresseSiege,
        String ville,
        String codePostal,
        String region,
        String telephone,
        String telephone2,
        String emailContact,
        String siteWeb,
        String linkedinUrl,

        // RGPD
        String    emailDpo,
        String    dpoNom,
        String    telephoneDpo,
        String    numeroCndp,
        String    numeroCnil,
        LocalDate dateDeclarationCndp,
        LocalDate dateDeclarationCnil,
        String    baseJuridiqueDefaut,
        Integer   dureeRetentionJours,

        // Licensing
        String    numeroAgrement,
        String    carteProfessionnelle,
        String    caisseGarantie,
        String    assuranceRc,
        LocalDate dateAgrement,
        LocalDate dateExpirationAgrement,
        String    typeActivite,
        String    zonesIntervention,

        // Branding
        String logoUrl,
        String couleurPrimaire,
        String couleurSecondaire,
        String langueDefaut,
        String devise,
        String fuseauHoraire,
        String formatDate,
        String mentionsLegales,

        // Subscription
        String    planAbonnement,
        Integer   maxUtilisateurs,
        Integer   maxBiens,
        Integer   maxContacts,
        Integer   maxProjets,
        LocalDate dateDebutAbonnement,
        LocalDate dateFinAbonnement,
        boolean   periodeEssai,

        // Lifecycle
        Instant dateSuspension,
        String  raisonSuspension,
        UUID    createdById,

        // Internal (SUPER_ADMIN only — R9: never in RGPD exports)
        String  notesInternes,

        int     complianceScore,
        Long    version,
        Instant createdAt,
        Instant updatedAt,
        String  logoDownloadUrl
) {
    public static SocieteDetailDto from(Societe s) {
        String logoDownloadUrl = s.getLogoFileKey() != null
                ? "/api/societes/" + s.getId() + "/logo"
                : null;
        return new SocieteDetailDto(
                s.getId(),
                s.getKey(),
                s.getNom(),
                s.getNomCommercial(),
                s.getFormeJuridique(),
                s.getCapitalSocial(),
                s.getSiretIce(),
                s.getRc(),
                s.getIfNumber(),
                s.getPatente(),
                s.getTvaNumber(),
                s.getCnssNumber(),
                s.getPays(),
                s.isActif(),
                s.getAdresse(),
                s.getAdresseSiege(),
                s.getVille(),
                s.getCodePostal(),
                s.getRegion(),
                s.getTelephone(),
                s.getTelephone2(),
                s.getEmailContact(),
                s.getSiteWeb(),
                s.getLinkedinUrl(),
                s.getEmailDpo(),
                s.getDpoNom(),
                s.getTelephoneDpo(),
                s.getNumeroCndp(),
                s.getNumeroCnil(),
                s.getDateDeclarationCndp(),
                s.getDateDeclarationCnil(),
                s.getBaseJuridiqueDefaut(),
                s.getDureeRetentionJours(),
                s.getNumeroAgrement(),
                s.getCarteProfessionnelle(),
                s.getCaisseGarantie(),
                s.getAssuranceRc(),
                s.getDateAgrement(),
                s.getDateExpirationAgrement(),
                s.getTypeActivite(),
                s.getZonesIntervention(),
                s.getLogoUrl(),
                s.getCouleurPrimaire(),
                s.getCouleurSecondaire(),
                s.getLangueDefaut(),
                s.getDevise(),
                s.getFuseauHoraire(),
                s.getFormatDate(),
                s.getMentionsLegales(),
                s.getPlanAbonnement(),
                s.getMaxUtilisateurs(),
                s.getMaxBiens(),
                s.getMaxContacts(),
                s.getMaxProjets(),
                s.getDateDebutAbonnement(),
                s.getDateFinAbonnement(),
                s.isPeriodeEssai(),
                s.getDateSuspension(),
                s.getRaisonSuspension(),
                s.getCreatedBy() != null ? s.getCreatedBy().getId() : null,
                s.getNotesInternes(),
                s.getComplianceScore(),
                s.getVersion(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                logoDownloadUrl
        );
    }
}
