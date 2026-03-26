package com.yem.hlm.backend.user.api.dto;

import com.yem.hlm.backend.user.domain.User;

import java.util.UUID;

/**
 * Self-service profile DTO — returned by {@code GET /api/me} and {@code PATCH /api/me}.
 * Includes all editable profile fields plus read-only identity / role info.
 */
public record UserProfileResponse(
        UUID    id,
        String  email,
        String  role,
        String  prenom,
        String  nomFamille,
        String  telephone,
        String  poste,
        String  langueInterface,
        String  photoUrl
) {
    public static UserProfileResponse from(User user, String role) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                role,
                user.getPrenom(),
                user.getNomFamille(),
                user.getTelephone(),
                user.getPoste(),
                user.getLangueInterface(),
                user.getPhotoUrl()
        );
    }
}
