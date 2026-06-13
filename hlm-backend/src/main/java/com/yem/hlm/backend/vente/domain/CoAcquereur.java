package com.yem.hlm.backend.vente.domain;

import com.yem.hlm.backend.contact.domain.SituationMatrimoniale;
import com.yem.hlm.backend.contact.domain.TypeAcquereur;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A co-buyer (co-acquéreur) attached to a vente. Wave 12 allows at most one per vente
 * (unique constraint societe_id+vente_id); the model is otherwise open to N.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "co_acquereur",
        uniqueConstraints = @UniqueConstraint(name = "uk_coacq_societe_vente", columnNames = {"societe_id", "vente_id"}),
        indexes = @Index(name = "idx_coacq_vente_societe", columnList = "vente_id,societe_id"))
public class CoAcquereur {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "vente_id", nullable = false)
    private UUID venteId;

    @Setter @Column(name = "nom", nullable = false, length = 100)    private String nom;
    @Setter @Column(name = "prenom", nullable = false, length = 100) private String prenom;
    @Setter @Column(name = "cin_numero", length = 20)                private String cinNumero;
    @Setter @Column(name = "cin_date_delivrance")                    private LocalDate cinDateDelivrance;
    @Setter @Column(name = "passeport_numero", length = 30)          private String passeportNumero;
    @Setter @Column(name = "date_naissance")                         private LocalDate dateNaissance;
    @Setter @Column(name = "nationalite", length = 50)               private String nationalite;
    @Setter @Column(name = "pays_residence", length = 50)            private String paysResidence;
    @Setter @Enumerated(EnumType.STRING)
    @Column(name = "situation_matrimoniale", length = 30)            private SituationMatrimoniale situationMatrimoniale;
    @Setter @Enumerated(EnumType.STRING)
    @Column(name = "type_acquereur", length = 20)                    private TypeAcquereur typeAcquereur;
    @Setter @Column(name = "email", length = 200)                    private String email;
    @Setter @Column(name = "telephone", length = 30)                 private String telephone;
    @Setter @Enumerated(EnumType.STRING)
    @Column(name = "role_acquereur", nullable = false, length = 30)  private RoleAcquereur roleAcquereur;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.roleAcquereur == null) this.roleAcquereur = RoleAcquereur.CO_ACQUEREUR;
    }

    public CoAcquereur(UUID societeId, UUID venteId, String nom, String prenom) {
        this.societeId = societeId;
        this.venteId   = venteId;
        this.nom       = nom;
        this.prenom    = prenom;
        this.roleAcquereur = RoleAcquereur.CO_ACQUEREUR;
    }
}
