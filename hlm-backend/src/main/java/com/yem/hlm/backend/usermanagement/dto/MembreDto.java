package com.yem.hlm.backend.usermanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record MembreDto(
    UUID    id,
    String  email,
    String  prenom,
    String  nomFamille,
    String  nomComplet,
    String  telephone,
    String  poste,
    String  role,
    boolean actif,
    boolean enabled,
    boolean compteBloque,
    Instant derniereConnexion,
    Instant dateAjout,
    Instant invitationEnvoyeeAt,
    Instant invitationExpireAt,
    String  statut,
    Long    version
) {}
