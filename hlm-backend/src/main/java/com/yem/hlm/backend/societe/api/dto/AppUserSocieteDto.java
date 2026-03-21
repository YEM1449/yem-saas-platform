package com.yem.hlm.backend.societe.api.dto;

import com.yem.hlm.backend.societe.domain.AppUserSociete;

import java.util.UUID;

public record AppUserSocieteDto(
        UUID userId,
        String role,
        boolean actif
) {
    public static AppUserSocieteDto from(AppUserSociete aus) {
        return new AppUserSocieteDto(aus.getUserId(), aus.getRole(), aus.isActif());
    }
}
