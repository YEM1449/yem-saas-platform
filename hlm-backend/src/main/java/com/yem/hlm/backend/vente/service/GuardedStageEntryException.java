package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.vente.domain.VenteStatut;

/**
 * Thrown when the generic {@code PATCH /api/ventes/{id}/statut} endpoint is used to enter a
 * stage that has a dedicated, guarded entry point.
 *
 * <p>Some VEFA stages carry legal/operational preconditions that the generic statut change does
 * not (and must not) enforce on its own:
 * <ul>
 *   <li>{@code OPTION} — property hold + agent + RG-B03 (one active vente) → {@code POST /api/ventes/option}</li>
 *   <li>{@code RESERVE}/{@code EN_RETRACTATION} — Art. 618-4 deposit cap (≤ 5%) + cooling-off window
 *       → {@code POST /api/ventes/{id}/confirm-reservation}</li>
 *   <li>{@code LIVRE_AVEC_RESERVES} — must record the delivery reserves
 *       → {@code POST /api/ventes/{id}/livraison}</li>
 *   <li>{@code RESERVES_LEVEES} — reached only by lifting the last reserve
 *       → {@code PUT /api/ventes/{id}/reserves/{reserveId}/lever}</li>
 * </ul>
 *
 * <p>Routing these through the generic endpoint would bypass those guards (EX-001 / Loi 44-00).
 * Mapped to HTTP 409 (GUARDED_STAGE_ENTRY).
 */
public class GuardedStageEntryException extends RuntimeException {
    public GuardedStageEntryException(VenteStatut target, String dedicatedEndpoint) {
        super("La transition vers " + target + " doit passer par l'action dédiée (" + dedicatedEndpoint
                + ") qui applique les contrôles légaux requis (Loi 44-00).");
    }
}
