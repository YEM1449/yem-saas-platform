package com.yem.hlm.backend.viewer3d.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Maps a GLB mesh name to a property (lot) record.
 * Unique per (societeId, projetId, meshId) — one mesh name can only map to one lot per project.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "lot_3d_mapping",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lot3dmapping_societe_projet_mesh",
                        columnNames = {"societe_id", "projet_id", "mesh_id"})
        },
        indexes = {
                @Index(name = "idx_lot3dmapping_projet_societe", columnList = "projet_id, societe_id"),
                @Index(name = "idx_lot3dmapping_property",      columnList = "property_id")
        }
)
public class Lot3dMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "projet_id", nullable = false)
    private UUID projetId;

    /** FK to property.id — the actual bookable unit. */
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    /** Matches mesh.name in the GLB scene graph. */
    @Column(name = "mesh_id", nullable = false, length = 255)
    private String meshId;

    /** Parent building mesh name (used for Immeuble drill-down in dashboard). */
    @Column(name = "immeuble_mesh_id", length = 255)
    private String immeubleMeshId;

    /** Parent tranche mesh name. */
    @Column(name = "tranche_mesh_id", length = 255)
    private String trancheMeshId;

    public Lot3dMapping(UUID societeId, UUID projetId, UUID propertyId,
                        String meshId, String immeubleMeshId, String trancheMeshId) {
        this.societeId      = societeId;
        this.projetId       = projetId;
        this.propertyId     = propertyId;
        this.meshId         = meshId;
        this.immeubleMeshId = immeubleMeshId;
        this.trancheMeshId  = trancheMeshId;
    }
}
