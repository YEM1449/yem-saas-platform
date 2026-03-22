package com.yem.hlm.backend.societe.api.dto;

/**
 * Operational statistics for a company — returned by {@code GET /api/admin/societes/{id}/stats}.
 */
public record SocieteStatsDto(
        long totalMembres,
        long membresActifs,
        long totalContacts,
        long totalBiens,
        long totalProjets,
        long totalContrats,

        // Quota usage (null when no quota set)
        Integer maxUtilisateurs,
        Integer maxBiens,
        Integer maxContacts,
        Integer maxProjets
) {}
