package com.yem.hlm.backend.visite.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A persistent, scheduled reminder for a {@link Visite} (RG-V07).
 *
 * <p>Reminders are <b>not</b> driven by an in-memory task scheduler (which is wiped on
 * redeploy). They live in the database and are scanned every 5 minutes by a
 * {@code @Scheduled} job which sends the email via Brevo and flips the row to
 * {@code ENVOYE}. Idempotent: an {@code ENVOYE} row is never re-sent. {@code tentatives}
 * caps retries on transient email failures.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "visite_rappel",
        indexes = {
                @Index(name = "idx_visiterappel_statut_dua", columnList = "statut,du_a"),
                @Index(name = "idx_visiterappel_visite",     columnList = "visite_id")
        }
)
public class VisiteRappel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "visite_id", nullable = false)
    private UUID visiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeRappel type;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinataire", nullable = false, length = 20)
    private DestinataireRappel destinataire;

    @Setter
    @Column(name = "du_a", nullable = false)
    private Instant duA;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutRappel statut = StatutRappel.EN_ATTENTE;

    @Setter
    @Column(name = "tentatives", nullable = false)
    private int tentatives = 0;

    @Setter
    @Column(name = "envoye_at")
    private Instant envoyeAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VisiteRappel(UUID societeId, UUID visiteId, TypeRappel type,
                        DestinataireRappel destinataire, Instant duA) {
        this.societeId = societeId;
        this.visiteId = visiteId;
        this.type = type;
        this.destinataire = destinataire;
        this.duA = duA;
        this.statut = StatutRappel.EN_ATTENTE;
        this.tentatives = 0;
        this.createdAt = Instant.now();
    }

    public void marquerEnvoye() {
        this.statut = StatutRappel.ENVOYE;
        this.envoyeAt = Instant.now();
    }

    public void marquerAnnule() {
        this.statut = StatutRappel.ANNULE;
    }

    public void incrementerTentative() {
        this.tentatives++;
    }
}
