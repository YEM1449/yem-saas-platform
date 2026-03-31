package com.yem.hlm.backend.immeuble.domain;

import com.yem.hlm.backend.project.domain.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An Immeuble (Building) within a real estate Project.
 * Hierarchy: Société → Project → Immeuble → Property (Unit).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "immeuble",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_immeuble_societe_project_nom",
                        columnNames = {"societe_id", "project_id", "nom"})
        },
        indexes = {
                @Index(name = "idx_immeuble_project", columnList = "project_id"),
                @Index(name = "idx_immeuble_societe", columnList = "societe_id")
        }
)
public class Immeuble {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Setter
    @Column(name = "nom", nullable = false, length = 255)
    private String nom;

    @Setter
    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Setter
    @Column(name = "nb_etages")
    private Integer nbEtages;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public Immeuble(UUID societeId, Project project, String nom) {
        this.societeId = societeId;
        this.project = project;
        this.nom = nom;
    }
}
