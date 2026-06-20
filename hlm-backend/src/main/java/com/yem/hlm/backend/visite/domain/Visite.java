package com.yem.hlm.backend.visite.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A commercial visit (rendez-vous) between an agent and a contact to present a
 * property or a project (RG-V01). The property and project are both optional —
 * a visite can be a simple discovery meeting.
 *
 * <p>All access is société-scoped. {@code dateHeure} is stored as an {@link Instant}
 * (TIMESTAMPTZ); it is rendered and entered in Africa/Casablanca time (RG-V10).
 *
 * <p>Lifecycle in {@link StatutVisite} (RG-V02). Reaching {@code REALISEE} requires a
 * {@code compteRendu} + {@code resultat} (RG-V06).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "visite",
        indexes = {
                @Index(name = "idx_visite_societe_agent_date",  columnList = "societe_id,agent_id,date_heure"),
                @Index(name = "idx_visite_societe_statut_date", columnList = "societe_id,statut,date_heure"),
                @Index(name = "idx_visite_contact",             columnList = "contact_id")
        }
)
public class Visite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false, foreignKey = @ForeignKey(name = "fk_visite_agent"))
    private User agent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_visite_contact"))
    private Contact contact;

    @Setter
    @Column(name = "property_id")
    private UUID propertyId;

    @Setter
    @Column(name = "project_id")
    private UUID projectId;

    @Setter
    @Column(name = "date_heure", nullable = false)
    private Instant dateHeure;

    @Setter
    @Column(name = "duree_minutes", nullable = false)
    private int dureeMinutes = 30;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeVisite type;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutVisite statut = StatutVisite.PLANIFIEE;

    @Setter
    @Column(name = "lieu", length = 255)
    private String lieu;

    @Setter
    @Column(name = "compte_rendu", columnDefinition = "TEXT")
    private String compteRendu;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "resultat", length = 30)
    private ResultatVisite resultat;

    @Setter
    @Column(name = "vente_id")
    private UUID venteId;

    @Setter
    @Column(name = "annulation_raison", length = 255)
    private String annulationRaison;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Visite(UUID societeId, User agent, Contact contact, Instant dateHeure,
                  int dureeMinutes, TypeVisite type, UUID createdByUserId) {
        this.societeId = societeId;
        this.agent = agent;
        this.contact = contact;
        this.dateHeure = dateHeure;
        this.dureeMinutes = dureeMinutes;
        this.type = type;
        this.statut = StatutVisite.PLANIFIEE;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }

    /** Exclusive end of the visit slot — used for conflict detection (RG-V05). */
    public Instant getFin() {
        return dateHeure.plusSeconds(dureeMinutes * 60L);
    }
}
