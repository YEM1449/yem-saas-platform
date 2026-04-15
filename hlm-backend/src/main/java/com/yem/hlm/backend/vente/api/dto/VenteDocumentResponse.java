package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.VenteDocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record VenteDocumentResponse(
        UUID id,
        UUID venteId,
        String nomFichier,
        String contentType,
        Long tailleOctets,
        UUID uploadedById,        // null for portal-uploaded documents
        boolean uploadedByPortal,
        VenteDocumentType documentType,
        LocalDateTime createdAt
) {}
