package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.contact.domain.SituationMatrimoniale;
import com.yem.hlm.backend.contact.domain.TypeAcquereur;
import com.yem.hlm.backend.vente.domain.CoAcquereur;
import com.yem.hlm.backend.vente.domain.RoleAcquereur;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CoAcquereurResponse(
        UUID id,
        UUID venteId,
        String nom,
        String prenom,
        String cinNumero,
        LocalDate cinDateDelivrance,
        String passeportNumero,
        LocalDate dateNaissance,
        String nationalite,
        String paysResidence,
        SituationMatrimoniale situationMatrimoniale,
        TypeAcquereur typeAcquereur,
        String email,
        String telephone,
        RoleAcquereur roleAcquereur,
        LocalDateTime createdAt
) {
    public static CoAcquereurResponse from(CoAcquereur c) {
        return new CoAcquereurResponse(
                c.getId(), c.getVenteId(), c.getNom(), c.getPrenom(), c.getCinNumero(),
                c.getCinDateDelivrance(), c.getPasseportNumero(), c.getDateNaissance(),
                c.getNationalite(), c.getPaysResidence(), c.getSituationMatrimoniale(),
                c.getTypeAcquereur(), c.getEmail(), c.getTelephone(), c.getRoleAcquereur(),
                c.getCreatedAt());
    }
}
