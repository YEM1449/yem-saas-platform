package com.yem.hlm.backend.vente.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VenteDocumentResponse(
        UUID id,
        UUID venteId,
        String nomFichier,
        String contentType,
        Long tailleOctets,
        UUID uploadedById,
        LocalDateTime createdAt
) {}
