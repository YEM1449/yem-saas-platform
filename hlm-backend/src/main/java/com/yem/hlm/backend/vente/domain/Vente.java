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

    /**
     * Human-readable unique reference (e.g. VTE-2026-1F3-00001).
     * Generated once on creation by {@link com.yem.hlm.backend.vente.service.VenteRefGenerator}; never mutable.
     */
    @Setter
    @Column(name = "vente_ref", nullable = false, length = 25, updatable = false)
    private String venteRef;

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

    @Setter
    @Column(name = "probability", nullable = false)
    private int probability = 25;

    @Setter
    @Column(name = "stage_entry_date", nullable = false)
    private LocalDateTime stageEntryDate;

    @Setter
    @Column(name = "expected_closing_date")
    private LocalDate expectedClosingDate;

    /**
     * Tracks the contract document lifecycle.
     * PENDING → GENERATED (after PDF creation) → SIGNED.
     * The "Signer" action is blocked unless contractStatus == GENERATED.
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", nullable = false, length = 20)
    private ContractStatus contractStatus = ContractStatus.PENDING;

    // ── French Legal Deadlines ────────────────────────────────────────────

    /** SRU cooling-off deadline = dateCompromis + 10 days (Art. L271-1 Code de la Construction). Auto-populated on create. */
    @Setter
    @Column(name = "date_fin_delai_sru")
    private LocalDate dateFinDelaiSru;

    /** Condition suspensive crédit deadline = dateCompromis + 45 days by default, overridable. */
    @Setter
    @Column(name = "date_limite_condition_credit")
    private LocalDate dateLimiteConditionCredit;

    // ── Financing Risk ────────────────────────────────────────────────────

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "type_financement", length = 30)
    private TypeFinancement typeFinancement;

    @Setter
    @Column(name = "montant_credit", precision = 15, scale = 2)
    private BigDecimal montantCredit;

    @Setter
    @Column(name = "banque_credit", length = 100)
    private String banqueCredit;

    @Setter
    @Column(name = "credit_obtenu", nullable = false)
    private boolean creditObtenu = false;

    // ── Cancellation Reason ───────────────────────────────────────────────

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "motif_annulation", length = 50)
    private MotifAnnulation motifAnnulation;

    // ── Notary Information ────────────────────────────────────────────────

    @Setter
    @Column(name = "notaire_acquereur_nom", length = 200)
    private String notaireAcquereurNom;

    @Setter
    @Column(name = "notaire_acquereur_email", length = 200)
    private String notaireAcquereurEmail;

    // ── Post-Livraison Tracking (Moroccan closing process) ────────────────

    /** Date of the PV de réception (procès-verbal) — buyer acceptance of the delivered unit. */
    @Setter
    @Column(name = "date_pv_reception")
    private LocalDate datePvReception;

    /** Date titre foncier obtained from the Conservation Foncière (land registry). */
    @Setter
    @Column(name = "date_titre_foncier")
    private LocalDate dateTitreFoncier;

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
        if (this.stageEntryDate == null) this.stageEntryDate = now;
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
