package com.yem.hlm.backend.common.event;

import java.util.UUID;

/**
 * Published when a VenteEcheance is added or updated (statut/date_paiement changed).
 * Triggers partial KPI recomputation: MONTANT_ENCAISSE, TAUX_RECOUVREMENT, SOLDE_RESTANT.
 */
public class EcheanceChangedEvent extends DomainEvent {

    private final UUID venteId;
    private final UUID trancheId;

    public EcheanceChangedEvent(UUID societeId, UUID actorUserId, UUID venteId, UUID trancheId) {
        super(societeId, actorUserId);
        this.venteId   = venteId;
        this.trancheId = trancheId;
    }

    public UUID getVenteId()   { return venteId; }
    public UUID getTrancheId() { return trancheId; }
}
