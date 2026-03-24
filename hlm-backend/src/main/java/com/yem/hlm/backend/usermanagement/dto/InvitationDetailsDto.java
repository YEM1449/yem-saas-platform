package com.yem.hlm.backend.usermanagement.dto;

public record InvitationDetailsDto(
    String prenom,
    String email,
    String societeNom,
    String role,
    String expireDans
) {}
