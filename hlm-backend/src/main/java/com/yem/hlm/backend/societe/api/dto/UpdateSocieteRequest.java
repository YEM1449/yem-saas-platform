package com.yem.hlm.backend.societe.api.dto;

public record UpdateSocieteRequest(
        String nom,
        String siretIce,
        String adresse,
        String emailDpo,
        String logoUrl,
        String pays
) {}
