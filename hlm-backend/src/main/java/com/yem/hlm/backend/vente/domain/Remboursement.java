package com.yem.hlm.backend.vente.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Suivi du remboursement du dépôt de garantie après annulation/rétractation d'une vente
 * (finding #028). Un enregistrement par vente. Créé automatiquement (statut {@code DU}) lors
 * de l'annulation, puis marqué {@code EFFECTUE} par un gestionnaire avec la date et le moyen.
 */
@Entity
@Table(name = "remboursement")
public class Remboursement {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "vente_id", nullable = false)
    private UUID venteId;

    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "moyen", length = 20)
    private MoyenRemboursement moyen;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutRemboursement statut = StatutRemboursement.DU;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "motif", length = 200)
    private String motif;

    @Column(name = "date_remboursement")
    private LocalDate dateRemboursement;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Remboursement() {}

    public Remboursement(UUID societeId, UUID venteId, BigDecimal montant, String motif, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.societeId = societeId;
        this.venteId = venteId;
        this.montant = montant;
        this.motif = motif;
        this.statut = StatutRemboursement.DU;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getSocieteId() { return societeId; }
    public UUID getVenteId() { return venteId; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public MoyenRemboursement getMoyen() { return moyen; }
    public void setMoyen(MoyenRemboursement moyen) { this.moyen = moyen; }
    public StatutRemboursement getStatut() { return statut; }
    public void setStatut(StatutRemboursement statut) { this.statut = statut; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public LocalDate getDateRemboursement() { return dateRemboursement; }
    public void setDateRemboursement(LocalDate dateRemboursement) { this.dateRemboursement = dateRemboursement; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
