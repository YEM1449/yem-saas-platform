package com.yem.hlm.backend.viewer3d.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata record for a GLB model uploaded for a project.
 * One model per (societeId, projetId) — enforced by unique constraint.
 * The GLB file itself lives in R2; only the object key is stored here.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "project_3d_model",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project3dmodel_societe_projet",
                        columnNames = {"societe_id", "projet_id"})
        },
        indexes = {
                @Index(name = "idx_project3dmodel_projet_societe", columnList = "projet_id, societe_id")
        }
)
public class Project3dModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "projet_id", nullable = false)
    private UUID projetId;

    /** R2 object key — never store the full URL here. */
    @Column(name = "glb_file_key", nullable = false, length = 1024)
    private String glbFileKey;

    @Column(name = "draco_compressed", nullable = false)
    private boolean dracoCompressed;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    public Project3dModel(UUID societeId, UUID projetId, String glbFileKey,
                          boolean dracoCompressed, UUID uploadedByUserId) {
        this.societeId         = societeId;
        this.projetId          = projetId;
        this.glbFileKey        = glbFileKey;
        this.dracoCompressed   = dracoCompressed;
        this.uploadedAt        = Instant.now();
        this.uploadedByUserId  = uploadedByUserId;
    }
}
