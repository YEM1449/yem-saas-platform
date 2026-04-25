package com.yem.hlm.backend.immeuble.api.dto;

import com.yem.hlm.backend.immeuble.domain.Immeuble;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImmeubleResponse(
        UUID id,
        UUID projectId,
        String projectName,
        String nom,
        String adresse,
        Integer nbEtages,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID trancheId
) {
    public static ImmeubleResponse from(Immeuble i) {
        return new ImmeubleResponse(
                i.getId(),
                i.getProject().getId(),
                i.getProject().getName(),
                i.getNom(),
                i.getAdresse(),
                i.getNbEtages(),
                i.getDescription(),
                i.getCreatedAt(),
                i.getUpdatedAt(),
                i.getTrancheId()
        );
    }
}
