package com.yem.hlm.backend.usermanagement.dto;

import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.user.domain.User;

import java.time.Instant;

public enum MembreStatut {
    ACTIF, INVITE, INVITATION_EXPIREE, BLOQUE, RETIRE;

    public static MembreStatut compute(User user, AppUserSociete aus) {
        if (!aus.isActif())                                          return RETIRE;
        if (user.isCompteBloque())                                   return BLOQUE;
        if (!user.isEnabled()) {
            if (user.getInvitationExpireAt() == null
                    || user.getInvitationExpireAt().isAfter(Instant.now()))
                                                                     return INVITE;
            return INVITATION_EXPIREE;
        }
        return ACTIF;
    }
}
