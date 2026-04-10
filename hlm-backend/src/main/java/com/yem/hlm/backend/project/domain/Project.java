package com.yem.hlm.backend.project.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Project groups one or more Properties under a named real-estate development
 * within a Société's portfolio (e.g. "Résidence Les Acacias", "Tour Marina").
 * <p>
 * Each Property MUST belong to exactly one Project (mandatory FK).
 * Deletion of a project is prevented at DB level if properties exist (FK RESTRICT).
 * Use {@link ProjectStatus#ARCHIVED} to retire a project instead.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "project",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project_tenant_name", columnNames = {"societe_id", "name"})
        },
        indexes = {
                @Index(name = "idx_project_tenant_id", columnList = "societe_id,id"),
                @Index(name = "idx_project_tenant_status", columnList = "societe_id,status")
        }
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Setter
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Optional street address for this project. */
    @Setter @Column(name = "adresse", length = 500)
    private String adresse;

    /** City where the project is located. */
    @Setter @Column(name = "ville", length = 100)
    private String ville;

    /** Postal code. */
    @Setter @Column(name = "code_postal", length = 20)
    private String codePostal;

    /** Cover image / logo for the project (stored via MediaStorageService). */
    @Setter @Column(name = "logo_file_key", length = 500)
    private String logoFileKey;

    @Setter @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Setter @Column(name = "logo_original_filename", length = 255)
    private String logoOriginalFilename;

    /** Maître d'ouvrage (project owner / developer name). */
    @Setter @Column(name = "maitre_ouvrage", length = 200)
    private String maitreOuvrage;

    /** Date d'ouverture commercialisation. */
    @Setter @Column(name = "date_ouverture_commercialisation")
    private LocalDate dateOuvertureCommercialisation;

    /** TVA applicable au projet (%). */
    @Setter @Column(name = "tva_taux", precision = 5, scale = 2)
    private BigDecimal tvaTaux;

    /** Surface totale du terrain (m²). */
    @Setter @Column(name = "surface_terrain_m2", precision = 12, scale = 2)
    private BigDecimal surfaceTerrainM2;

    /** Prix moyen au m² cible (MAD/m²). */
    @Setter @Column(name = "prix_moyen_m2_cible", precision = 12, scale = 2)
    private BigDecimal prixMoyenM2Cible;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ProjectStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Creates a new active project.
     *
     * @param societeId the owning société
     * @param name      the project name (unique per société)
     */
    public Project(UUID societeId, String name) {
        this.societeId = societeId;
        this.name = name;
        this.status = ProjectStatus.ACTIVE;
    }
}
