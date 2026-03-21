package com.yem.hlm.backend.societe.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "societe")
public class Societe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key", unique = true, length = 80)
    private String key;

    @Column(name = "nom", nullable = false, length = 255)
    private String nom;

    @Column(name = "siret_ice", length = 50)
    private String siretIce;

    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Column(name = "email_dpo", length = 255)
    private String emailDpo;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "pays", nullable = false, length = 10)
    private String pays = "MA";

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Societe() {}

    public Societe(String nom, String pays) {
        this.nom = nom;
        this.pays = pays;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getSiretIce() { return siretIce; }
    public void setSiretIce(String siretIce) { this.siretIce = siretIce; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getEmailDpo() { return emailDpo; }
    public void setEmailDpo(String emailDpo) { this.emailDpo = emailDpo; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
