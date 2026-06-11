package com.yem.hlm.backend.vente.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Financing file for a vente — one per vente (unique vente_id). Carries the multi-status
 * workflow and dates the flat {@code vente.*} financing fields don't hold.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "dossier_financement",
        uniqueConstraints = @UniqueConstraint(name = "uk_dosfin_vente", columnNames = "vente_id"),
        indexes = @Index(name = "idx_dosfin_societe", columnList = "societe_id"))
public class DossierFinancement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "vente_id", nullable = false)
    private UUID venteId;

    @Setter @Enumerated(EnumType.STRING)
    @Column(name = "type_financement", length = 30)         private TypeFinancement typeFinancement;
    @Setter @Column(name = "banque", length = 100)          private String banque;
    @Setter @Column(name = "montant_credit", precision = 15, scale = 2) private BigDecimal montantCredit;
    @Setter @Column(name = "taux_interet", precision = 5, scale = 4)    private BigDecimal tauxInteret;
    @Setter @Column(name = "duree_mois")                    private Integer dureeMois;
    @Setter @Column(name = "apport_personnel", precision = 15, scale = 2) private BigDecimal apportPersonnel;
    @Setter @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30) private StatutDossierFinancement statut;
    @Setter @Column(name = "date_demande")                  private LocalDate dateDemande;
    @Setter @Column(name = "date_accord")                   private LocalDate dateAccord;
    @Setter @Column(name = "date_expiration_accord")        private LocalDate dateExpirationAccord;
    @Setter @Column(name = "commentaire", columnDefinition = "TEXT") private String commentaire;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.statut == null) this.statut = StatutDossierFinancement.EN_COURS;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public DossierFinancement(UUID societeId, UUID venteId) {
        this.societeId = societeId;
        this.venteId   = venteId;
        this.statut    = StatutDossierFinancement.EN_COURS;
    }
}
