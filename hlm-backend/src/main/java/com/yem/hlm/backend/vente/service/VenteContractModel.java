package com.yem.hlm.backend.vente.service;

import java.time.format.DateTimeFormatter;

public record VenteContractModel(
    String societeName,
    String venteRef,
    String propertyRef,       // nullable
    String propertyTitle,     // nullable
    String propertyType,      // nullable
    String prixVente,
    String buyerName,
    String buyerPhone,        // nullable
    String buyerEmail,        // nullable
    String buyerAddress,      // nullable
    String buyerNationalId,   // nullable
    String agentName,
    String agentEmail,
    String dateCompromis,
    String dateActeNotarie,     // nullable
    String dateLivraisonPrevue, // nullable
    String statut,
    String contractStatus,
    String generatedAt
) {
    static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
}
