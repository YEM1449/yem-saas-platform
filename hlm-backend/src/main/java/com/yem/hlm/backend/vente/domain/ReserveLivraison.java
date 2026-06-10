package com.yem.hlm.backend.vente.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A reserve raised at delivery (réserve de livraison, Loi 44-00). Raised when a vente
 * transitions to {@code LIVRE_AVEC_RESERVES}; once every reserve is {@code LEVEE} the
 * vente can advance to {@code RESERVES_LEVEES}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "reserve_livraison",
        indexes = @Index(name = "idx_reservelivraison_vente_societe", columnList = "vente_id,societe_id")
)
public class ReserveLivraison {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "vente_id", nullable = false)
    private UUID venteId;

    @Setter
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutReserve statut;

    @Setter
    @Column(name = "date_constat", nullable = false)
    private LocalDate dateConstat;

    @Setter
    @Column(name = "date_levee_prevue")
    private LocalDate dateLeveePrevue;

    @Setter
    @Column(name = "date_levee_reelle")
    private LocalDate dateLeveeReelle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ReserveLivraison(UUID societeId, UUID venteId, String description, LocalDate dateLeveePrevue) {
        this.societeId       = societeId;
        this.venteId         = venteId;
        this.description     = description;
        this.statut          = StatutReserve.EN_ATTENTE;
        this.dateConstat     = LocalDate.now();
        this.dateLeveePrevue = dateLeveePrevue;
    }
}
