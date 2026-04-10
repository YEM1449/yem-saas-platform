package com.yem.hlm.backend.vente.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Master sales record representing one property sold to one buyer.
 *
 * <p>A Vente is typically created by converting a Reservation (post-deposit stage).
 * It tracks the full commercial pipeline from compromis to livraison.
 *
 * <p>Lifecycle: COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE (or ANNULE at any point)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "vente",
        indexes = {
                @Index(name = "idx_vente_societe_id",       columnList = "societe_id,id"),
                @Index(name = "idx_vente_societe_statut",   columnList = "societe_id,statut"),
                @Index(name = "idx_vente_societe_property", columnList = "societe_id,property_id"),
                @Index(name = "idx_vente_societe_contact",  columnList = "societe_id,contact_id"),
                @Index(name = "idx_vente_societe_agent",    columnList = "societe_id,agent_id")
        }
)
public class Vente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vente_contact"))
    private Contact contact;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vente_agent"))
    private User agent;

    /** Back-link to the reservation this vente originated from (null if created directly). */
    @Setter
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private VenteStatut statut;

    @Setter
    @Column(name = "prix_vente", precision = 15, scale = 2)
    private BigDecimal prixVente;

    @Setter
    @Column(name = "date_compromis")
    private LocalDate dateCompromis;

    @Setter
    @Column(name = "date_acte_notarie")
    private LocalDate dateActeNotarie;

    @Setter
    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;

    @Setter
    @Column(name = "date_livraison_reelle")
    private LocalDate dateLivraisonReelle;

    @Setter
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Tracks the contract document lifecycle.
     * PENDING → GENERATED (after PDF creation) → SIGNED.
     * The "Signer" action is blocked unless contractStatus == GENERATED.
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", nullable = false, length = 20)
    private ContractStatus contractStatus = ContractStatus.PENDING;

    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dateEcheance ASC")
    private List<VenteEcheance> echeances = new ArrayList<>();

    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<VenteDocument> documents = new ArrayList<>();

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

    public Vente(UUID societeId, UUID propertyId, Contact contact, User agent) {
        this.societeId  = societeId;
        this.propertyId = propertyId;
        this.contact    = contact;
        this.agent      = agent;
        this.statut     = VenteStatut.COMPROMIS;
    }
}
