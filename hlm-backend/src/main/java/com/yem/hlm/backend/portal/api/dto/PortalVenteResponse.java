package com.yem.hlm.backend.portal.api.dto;

import com.yem.hlm.backend.vente.api.dto.EcheanceResponse;
import com.yem.hlm.backend.vente.api.dto.VenteDocumentResponse;
import com.yem.hlm.backend.vente.domain.VenteStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only vente summary exposed to the buyer via the portal (ROLE_PORTAL).
 * Excludes internal fields (agent, societeId) — shows only buyer-relevant data.
 */
public record PortalVenteResponse(
        UUID id,
        UUID propertyId,
        VenteStatut statut,
        BigDecimal prixVente,
        LocalDate dateCompromis,
        LocalDate dateActeNotarie,
        LocalDate dateLivraisonPrevue,
        LocalDate dateLivraisonReelle,
        List<EcheanceResponse> echeances,
        List<VenteDocumentResponse> documents,
        LocalDateTime createdAt
) {}
