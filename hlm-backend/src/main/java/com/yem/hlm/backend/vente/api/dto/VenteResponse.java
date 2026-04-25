package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.ContractStatus;
import com.yem.hlm.backend.vente.domain.MotifAnnulation;
import com.yem.hlm.backend.vente.domain.TypeFinancement;
import com.yem.hlm.backend.vente.domain.VenteStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VenteResponse(
        UUID id,
        /** Human-readable unique reference, e.g. VTE-2026-ABC-00001. */
        String venteRef,
        UUID societeId,
        UUID propertyId,
        UUID contactId,
        /** Denormalised contact full name for UI display. */
        String contactFullName,
        UUID agentId,
        UUID reservationId,
        VenteStatut statut,
        ContractStatus contractStatus,
        // Legal milestone dates
        LocalDate dateFinDelaiReflexion,
        LocalDate dateLimiteFinancement,
        // Financing risk
        TypeFinancement typeFinancement,
        BigDecimal montantCredit,
        String banqueCredit,
        boolean creditObtenu,
        // Cancellation reason
        MotifAnnulation motifAnnulation,
        // Notary information
        String notaireAcquereurNom,
        String notaireAcquereurEmail,
        // Post-livraison tracking (Moroccan closing process)
        LocalDate datePvReception,
        LocalDate dateTitreFoncier,
        BigDecimal prixVente,
        LocalDate dateCompromis,
        LocalDate dateActeNotarie,
        LocalDate dateLivraisonPrevue,
        LocalDate dateLivraisonReelle,
        String notes,
        int probability,
        LocalDateTime stageEntryDate,
        LocalDate expectedClosingDate,
        List<EcheanceResponse> echeances,
        List<VenteDocumentResponse> documents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
