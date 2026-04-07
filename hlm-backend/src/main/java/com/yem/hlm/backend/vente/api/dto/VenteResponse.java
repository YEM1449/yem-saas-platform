package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.VenteStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VenteResponse(
        UUID id,
        UUID societeId,
        UUID propertyId,
        UUID contactId,
        /** Denormalised contact full name for UI display. */
        String contactFullName,
        UUID agentId,
        UUID reservationId,
        VenteStatut statut,
        BigDecimal prixVente,
        LocalDate dateCompromis,
        LocalDate dateActeNotarie,
        LocalDate dateLivraisonPrevue,
        LocalDate dateLivraisonReelle,
        String notes,
        List<EcheanceResponse> echeances,
        List<VenteDocumentResponse> documents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
