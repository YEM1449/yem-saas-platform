package com.yem.hlm.backend.societe.api.dto;

/**
 * Query parameters for {@code GET /api/admin/societes}.
 * All fields optional — null means "no filter".
 */
public record SocieteFilter(
        String  search,          // matches nom, nomCommercial, siretIce (case-insensitive)
        String  pays,
        String  planAbonnement,
        Boolean actif
) {}
