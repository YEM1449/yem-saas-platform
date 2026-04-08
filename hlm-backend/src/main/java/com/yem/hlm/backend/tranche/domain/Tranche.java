package com.yem.hlm.backend.tranche.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Tranche is a phased delivery group within a Project.
 * <p>
 * In French real estate, large programmes are split into tranches —
 * each grouping one or more Immeubles (buildings) that are constructed
 * and delivered together at a specific target date.
 * <p>
 * Hierarchy: Société → Project → Tranche → Immeuble → Property (lot)
 * <p>
 * The {@link #dateLivraisonPrevue} is contractually significant:
 * it is printed on buyer reservation contracts.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "tranche",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tranche_project_numero",
                        columnNames = {"project_id", "numero"})
        },
        indexes = {
                @Index(name = "idx_tranche_project",  columnList = "project_id"),
                @Index(name = "idx_tranche_societe",  columnList = "societe_id"),
                @Index(name = "idx_tranche_statut",   columnList = "statut")
        }
)
public class Tranche {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Sequential number within the project: 1, 2, 3… */
    @Setter
    @Column(name = "numero", nullable = false)
    private Integer numero;

    /**
     * Optional label for this tranche.
     * Defaults to "Tranche {numero}" if null.
     */
    @Setter
    @Column(name = "nom", length = 255)
    private String nom;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private TrancheStatut statut;

    /** Promised delivery date — printed on reservation contracts. */
    @Setter
    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;

    /** Actual delivery date, set when LIVREE. */
    @Setter
    @Column(name = "date_livraison_eff")
    private LocalDate dateLivraisonEff;

    /** Construction start date for this tranche. */
    @Setter
    @Column(name = "date_debut_travaux")
    private LocalDate dateDebutTravaux;

    /** Building permit reference (e.g. "PC 13055 25 00123"). */
    @Setter
    @Column(name = "permis_construire_ref", length = 100)
    private String permisConstruireRef;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
        if (this.statut == null) this.statut = TrancheStatut.EN_PREPARATION;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Tranche(UUID societeId, UUID projectId, Integer numero) {
        this.societeId = societeId;
        this.projectId = projectId;
        this.numero    = numero;
        this.statut    = TrancheStatut.EN_PREPARATION;
    }

    /** Display name — falls back to "Tranche {numero}" when nom is blank. */
    public String getDisplayNom() {
        return (nom != null && !nom.isBlank()) ? nom : "Tranche " + numero;
    }
}
