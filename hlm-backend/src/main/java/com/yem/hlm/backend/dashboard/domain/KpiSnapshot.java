package com.yem.hlm.backend.dashboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Latest computed KPI values for one (société, tranche) pair.
 * Upserted by {@link com.yem.hlm.backend.dashboard.service.KpiComputationService}
 * after each triggering event (sale finalized, écheance added/updated).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "kpi_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_kpi_snapshot_societe_tranche",
                columnNames = {"societe_id", "tranche_id"}))
public class KpiSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "tranche_id", nullable = false)
    private UUID trancheId;

    /** % of units sold or reserved. */
    @Setter
    @Column(name = "taux_commercialisation", precision = 5, scale = 2)
    private BigDecimal tauxCommercialisation;

    /** Total amount received from PAID écheances. */
    @Setter
    @Column(name = "montant_encaisse", precision = 15, scale = 2)
    private BigDecimal montantEncaisse;

    /** montantEncaisse / totalDu * 100. */
    @Setter
    @Column(name = "taux_recouvrement", precision = 5, scale = 2)
    private BigDecimal tauxRecouvrement;

    /** Sum of unpaid écheances. */
    @Setter
    @Column(name = "solde_restant", precision = 15, scale = 2)
    private BigDecimal soldeRestant;

    /** Average days from reservation to vente creation. */
    @Setter
    @Column(name = "delai_moyen_vente_jours")
    private Integer delaiMoyenVenteJours;

    @Setter
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    public KpiSnapshot(UUID societeId, UUID trancheId) {
        this.societeId = societeId;
        this.trancheId = trancheId;
        this.computedAt = LocalDateTime.now();
    }
}
