package com.yem.hlm.backend.visite.api.dto;

import com.yem.hlm.backend.visite.domain.ResultatVisite;
import com.yem.hlm.backend.visite.domain.StatutVisite;
import com.yem.hlm.backend.visite.domain.TypeVisite;
import com.yem.hlm.backend.visite.domain.Visite;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a {@link Visite}. {@code dateHeure} is an instant; the frontend renders it
 * in Africa/Casablanca (RG-V10). Names are denormalised for list/agenda display.
 */
public record VisiteResponse(
        UUID id,
        UUID agentId,
        String agentNom,
        UUID contactId,
        String contactNom,
        UUID propertyId,
        UUID projectId,
        Instant dateHeure,
        int dureeMinutes,
        TypeVisite type,
        StatutVisite statut,
        String lieu,
        String compteRendu,
        ResultatVisite resultat,
        UUID venteId,
        String annulationRaison,
        Instant createdAt
) {
    public static VisiteResponse from(Visite v) {
        var agent = v.getAgent();
        String agentNom = agent == null ? null
                : ((agent.getPrenom() == null ? "" : agent.getPrenom() + " ")
                   + (agent.getNomFamille() == null ? "" : agent.getNomFamille())).trim();
        String contactNom = v.getContact() == null ? null : v.getContact().getFullName();
        return new VisiteResponse(
                v.getId(),
                agent == null ? null : agent.getId(),
                (agentNom == null || agentNom.isBlank()) ? (agent == null ? null : agent.getEmail()) : agentNom,
                v.getContact() == null ? null : v.getContact().getId(),
                contactNom,
                v.getPropertyId(),
                v.getProjectId(),
                v.getDateHeure(),
                v.getDureeMinutes(),
                v.getType(),
                v.getStatut(),
                v.getLieu(),
                v.getCompteRendu(),
                v.getResultat(),
                v.getVenteId(),
                v.getAnnulationRaison(),
                v.getCreatedAt()
        );
    }
}
