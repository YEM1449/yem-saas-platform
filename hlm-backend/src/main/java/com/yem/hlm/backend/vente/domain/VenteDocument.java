package com.yem.hlm.backend.vente.domain;

import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A document attached to a Vente (e.g. compromis PDF, attestation financement).
 *
 * <p>The file itself is stored in object storage (S3/R2) or local disk;
 * this entity holds the metadata and the storage key needed to retrieve it.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "vente_document",
        indexes = {
                @Index(name = "idx_vdoc_vente_id",   columnList = "vente_id"),
                @Index(name = "idx_vdoc_societe_id", columnList = "societe_id")
        }
)
public class VenteDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "vente_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vdoc_vente"))
    private Vente vente;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    /** Path/key used to retrieve the file from storage. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false, foreignKey = @ForeignKey(name = "fk_vdoc_uploaded_by"))
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public VenteDocument(UUID societeId, Vente vente, String nomFichier,
                         String storageKey, String contentType, Long tailleOctets, User uploadedBy) {
        this.societeId   = societeId;
        this.vente       = vente;
        this.nomFichier  = nomFichier;
        this.storageKey  = storageKey;
        this.contentType = contentType;
        this.tailleOctets = tailleOctets;
        this.uploadedBy  = uploadedBy;
    }
}
