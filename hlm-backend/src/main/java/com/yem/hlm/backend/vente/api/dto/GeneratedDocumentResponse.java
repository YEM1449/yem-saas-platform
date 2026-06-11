package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.vente.domain.VenteDocumentType;

import java.util.UUID;

/** Result of generating a legal document — the stored VenteDocument reference. */
public record GeneratedDocumentResponse(
        UUID documentId,
        String nomFichier,
        VenteDocumentType documentType
) {}
