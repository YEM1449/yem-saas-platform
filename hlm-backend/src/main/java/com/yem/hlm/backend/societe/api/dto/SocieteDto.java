package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.Societe;

import java.time.Instant;
import java.util.UUID;

public record SocieteDto(
        UUID id,
        String nom,
        String siretIce,
        String adresse,
        String emailDpo,
        String logoUrl,
        String pays,
        boolean actif,
        Instant createdAt,
        Instant updatedAt
) {
    public static SocieteDto from(Societe s) {
        return new SocieteDto(
                s.getId(), s.getNom(), s.getSiretIce(), s.getAdresse(),
                s.getEmailDpo(), s.getLogoUrl(), s.getPays(), s.isActif(),
                s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
