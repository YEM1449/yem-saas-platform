package com.yem.hlm.backend.common.event;

import java.util.UUID;

/**
 * Published when a Vente reaches LIVRE status (sale fully finalized).
 * Triggers KPI recomputation for all scopes on the linked tranche.
 */
public class SaleFinalizedEvent extends DomainEvent {

    private final UUID venteId;
    private final UUID trancheId;

    public SaleFinalizedEvent(UUID societeId, UUID actorUserId, UUID venteId, UUID trancheId) {
        super(societeId, actorUserId);
        this.venteId   = venteId;
        this.trancheId = trancheId;
    }

    public UUID getVenteId()   { return venteId; }
    public UUID getTrancheId() { return trancheId; }
}
