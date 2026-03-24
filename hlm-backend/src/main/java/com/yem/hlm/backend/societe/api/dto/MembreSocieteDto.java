package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.AppUserSociete;

import java.time.Instant;
import java.util.UUID;

/**
 * Enriched member view used by the admin member-list endpoint.
 */
public record MembreSocieteDto(
        UUID    userId,
        String  prenom,
        String  nom,
        String  email,
        String  role,
        boolean actif,
        Instant dateAjout,
        Instant dateRetrait
) {
    public static MembreSocieteDto from(AppUserSociete aus) {
        return new MembreSocieteDto(
                aus.getUserId(),
                null,
                null,
                null,
                aus.getRole(),
                aus.isActif(),
                aus.getDateAjout(),
                aus.getDateRetrait()
        );
    }

    /** Full constructor used when User data is available. */
    public static MembreSocieteDto from(AppUserSociete aus,
                                        String prenom,
                                        String nom,
                                        String email) {
        return new MembreSocieteDto(
                aus.getUserId(),
                prenom,
                nom,
                email,
                aus.getRole(),
                aus.isActif(),
                aus.getDateAjout(),
                aus.getDateRetrait()
        );
    }
}
