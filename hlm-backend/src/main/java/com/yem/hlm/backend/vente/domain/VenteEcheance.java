package com.yem.hlm.backend.vente.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payment milestone (échéance) within a Vente's échéancier.
 *
 * <p>Examples: "Apport initial", "Versement notaire", "Solde à la livraison".
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "vente_echeance",
        indexes = {
                @Index(name = "idx_vech_vente_id",   columnList = "vente_id"),
                @Index(name = "idx_vech_societe_id", columnList = "societe_id")
        }
)
public class VenteEcheance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "vente_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vech_vente"))
    private Vente vente;

    @Setter
    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Setter
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Setter
    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private EcheanceStatut statut;

    @Setter
    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    @Setter
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public VenteEcheance(UUID societeId, Vente vente, String libelle, BigDecimal montant, LocalDate dateEcheance) {
        this.societeId    = societeId;
        this.vente        = vente;
        this.libelle      = libelle;
        this.montant      = montant;
        this.dateEcheance = dateEcheance;
        this.statut       = EcheanceStatut.EN_ATTENTE;
    }
}
