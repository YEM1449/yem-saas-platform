package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.EcheanceStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EcheanceResponse(
        UUID id,
        UUID venteId,
        String libelle,
        BigDecimal montant,
        LocalDate dateEcheance,
        EcheanceStatut statut,
        LocalDate datePaiement,
        String notes,
        LocalDateTime createdAt
) {}
