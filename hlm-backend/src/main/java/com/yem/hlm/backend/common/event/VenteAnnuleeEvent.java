package com.yem.hlm.backend.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a Vente is cancelled (rétractation or annulation, #028). Triggers automatic
 * creation of a {@code DU} refund record so the deposit's return is never silently forgotten.
 *
 * @param montantDepot the deposit to refund (may be null/zero — the gestionnaire confirms it).
 */
public class VenteAnnuleeEvent extends DomainEvent {

    private final UUID venteId;
    private final BigDecimal montantDepot;

    public VenteAnnuleeEvent(UUID societeId, UUID actorUserId, UUID venteId, BigDecimal montantDepot) {
        super(societeId, actorUserId);
        this.venteId = venteId;
        this.montantDepot = montantDepot;
    }

    public UUID getVenteId() { return venteId; }
    public BigDecimal getMontantDepot() { return montantDepot; }
}
