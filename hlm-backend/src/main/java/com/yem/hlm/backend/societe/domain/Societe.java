package com.yem.hlm.backend.societe.domain;

import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
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

    // ── Legal identity ────────────────────────────────────────────────────────

    @Column(name = "nom_commercial", length = 255)
    private String nomCommercial;

    @Column(name = "forme_juridique", length = 50)
    private String formeJuridique;

    @Column(name = "capital_social")
    private Long capitalSocial;

    @Column(name = "rc", length = 100)
    private String rc;

    @Column(name = "if_number", length = 50)
    private String ifNumber;

    @Column(name = "patente", length = 50)
    private String patente;

    @Column(name = "tva_number", length = 50)
    private String tvaNumber;

    @Column(name = "cnss_number", length = 50)
    private String cnssNumber;

    @Column(name = "siret_ice", length = 50)
    private String siretIce;

    // ── Location ──────────────────────────────────────────────────────────────

    /** Kept for backwards compatibility with existing queries. */
    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Column(name = "adresse_siege", columnDefinition = "TEXT")
    private String adresseSiege;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "code_postal", length = 20)
    private String codePostal;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "telephone2", length = 30)
    private String telephone2;

    @Column(name = "email_contact", length = 255)
    private String emailContact;

    @Column(name = "site_web", length = 500)
    private String siteWeb;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    // ── RGPD ──────────────────────────────────────────────────────────────────

    @Column(name = "email_dpo", length = 255)
    private String emailDpo;

    @Column(name = "dpo_nom", length = 255)
    private String dpoNom;

    @Column(name = "telephone_dpo", length = 30)
    private String telephoneDpo;

    @Column(name = "numero_cndp", length = 100)
    private String numeroCndp;

    @Column(name = "numero_cnil", length = 100)
    private String numeroCnil;

    @Column(name = "date_declaration_cndp")
    private LocalDate dateDeclarationCndp;

    @Column(name = "date_declaration_cnil")
    private LocalDate dateDeclarationCnil;

    @Column(name = "base_juridique_defaut", length = 50)
    private String baseJuridiqueDefaut;

    @Column(name = "duree_retention_jours")
    private Integer dureeRetentionJours;

    // ── Real-estate licensing ──────────────────────────────────────────────────

    @Column(name = "numero_agrement", length = 100)
    private String numeroAgrement;

    @Column(name = "carte_professionnelle", length = 100)
    private String carteProfessionnelle;

    @Column(name = "caisse_garantie", length = 200)
    private String caisseGarantie;

    @Column(name = "assurance_rc", length = 200)
    private String assuranceRc;

    @Column(name = "date_agrement")
    private LocalDate dateAgrement;

    @Column(name = "date_expiration_agrement")
    private LocalDate dateExpirationAgrement;

    @Column(name = "type_activite", length = 100)
    private String typeActivite;

    @Column(name = "zones_intervention", columnDefinition = "TEXT")
    private String zonesIntervention;

    // ── Branding ──────────────────────────────────────────────────────────────

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_file_key", length = 500)
    private String logoFileKey;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "couleur_primaire", length = 7)
    private String couleurPrimaire;

    @Column(name = "couleur_secondaire", length = 7)
    private String couleurSecondaire;

    @Column(name = "langue_defaut", length = 10)
    private String langueDefaut;

    @Column(name = "devise", length = 3)
    private String devise;

    @Column(name = "fuseau_horaire", length = 50)
    private String fuseauHoraire;

    @Column(name = "format_date", length = 20)
    private String formatDate;

    @Column(name = "mentions_legales", columnDefinition = "TEXT")
    private String mentionsLegales;

    // ── Subscription & quotas ─────────────────────────────────────────────────

    @Column(name = "plan_abonnement", length = 50)
    private String planAbonnement = "STARTER";

    @Column(name = "max_utilisateurs")
    private Integer maxUtilisateurs;

    @Column(name = "max_biens")
    private Integer maxBiens;

    @Column(name = "max_contacts")
    private Integer maxContacts;

    @Column(name = "max_projets")
    private Integer maxProjets;

    @Column(name = "date_debut_abonnement")
    private LocalDate dateDebutAbonnement;

    @Column(name = "date_fin_abonnement")
    private LocalDate dateFinAbonnement;

    @Column(name = "periode_essai", nullable = false)
    private boolean periodeEssai = true;

    // ── Commercial targets (Wave 13 — Owner Executive View) ───────────────────

    @Column(name = "ca_mensuel_cible", precision = 14, scale = 2)
    private java.math.BigDecimal caMensuelCible;

    @Column(name = "ventes_mensuel_cible")
    private Integer ventesMensuelCible;

    // ── Core fields ───────────────────────────────────────────────────────────

    @Column(name = "pays", nullable = false, length = 10)
    private String pays = "MA";

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    // ── Lifecycle & audit ─────────────────────────────────────────────────────

    @Column(name = "date_suspension")
    private Instant dateSuspension;

    @Column(name = "raison_suspension", columnDefinition = "TEXT")
    private String raisonSuspension;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /** Visible by SUPER_ADMIN only — never included in RGPD exports (R9). */
    @Column(name = "notes_internes", columnDefinition = "TEXT")
    private String notesInternes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── Optimistic locking ────────────────────────────────────────────────────

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Societe() {}

    public Societe(String nom, String pays) {
        this.nom = nom;
        this.pays = pays;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // ── RGPD compliance score (0–100) ─────────────────────────────────────────

    public int getComplianceScore() {
        int score = 0;
        if (nom != null && !nom.isBlank())                       score += 20;
        if (emailDpo != null && !emailDpo.isBlank())             score += 20;
        if (adresseSiege != null || adresse != null)             score += 10;
        if (numeroCndp != null || numeroCnil != null)            score += 30;
        if (dpoNom != null && !dpoNom.isBlank())                 score += 10;
        if (baseJuridiqueDefaut != null)                         score += 10;
        return score;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getNomCommercial() { return nomCommercial; }
    public void setNomCommercial(String nomCommercial) { this.nomCommercial = nomCommercial; }
    public String getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(String formeJuridique) { this.formeJuridique = formeJuridique; }
    public Long getCapitalSocial() { return capitalSocial; }
    public void setCapitalSocial(Long capitalSocial) { this.capitalSocial = capitalSocial; }
    public String getRc() { return rc; }
    public void setRc(String rc) { this.rc = rc; }
    public String getIfNumber() { return ifNumber; }
    public void setIfNumber(String ifNumber) { this.ifNumber = ifNumber; }
    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }
    public String getTvaNumber() { return tvaNumber; }
    public void setTvaNumber(String tvaNumber) { this.tvaNumber = tvaNumber; }
    public String getCnssNumber() { return cnssNumber; }
    public void setCnssNumber(String cnssNumber) { this.cnssNumber = cnssNumber; }
    public String getSiretIce() { return siretIce; }
    public void setSiretIce(String siretIce) { this.siretIce = siretIce; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getAdresseSiege() { return adresseSiege; }
    public void setAdresseSiege(String adresseSiege) { this.adresseSiege = adresseSiege; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public String getCodePostal() { return codePostal; }
    public void setCodePostal(String codePostal) { this.codePostal = codePostal; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getTelephone2() { return telephone2; }
    public void setTelephone2(String telephone2) { this.telephone2 = telephone2; }
    public String getEmailContact() { return emailContact; }
    public void setEmailContact(String emailContact) { this.emailContact = emailContact; }
    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String siteWeb) { this.siteWeb = siteWeb; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getEmailDpo() { return emailDpo; }
    public void setEmailDpo(String emailDpo) { this.emailDpo = emailDpo; }
    public String getDpoNom() { return dpoNom; }
    public void setDpoNom(String dpoNom) { this.dpoNom = dpoNom; }
    public String getTelephoneDpo() { return telephoneDpo; }
    public void setTelephoneDpo(String telephoneDpo) { this.telephoneDpo = telephoneDpo; }
    public String getNumeroCndp() { return numeroCndp; }
    public void setNumeroCndp(String numeroCndp) { this.numeroCndp = numeroCndp; }
    public String getNumeroCnil() { return numeroCnil; }
    public void setNumeroCnil(String numeroCnil) { this.numeroCnil = numeroCnil; }
    public LocalDate getDateDeclarationCndp() { return dateDeclarationCndp; }
    public void setDateDeclarationCndp(LocalDate d) { this.dateDeclarationCndp = d; }
    public LocalDate getDateDeclarationCnil() { return dateDeclarationCnil; }
    public void setDateDeclarationCnil(LocalDate d) { this.dateDeclarationCnil = d; }
    public String getBaseJuridiqueDefaut() { return baseJuridiqueDefaut; }
    public void setBaseJuridiqueDefaut(String baseJuridiqueDefaut) { this.baseJuridiqueDefaut = baseJuridiqueDefaut; }
    public Integer getDureeRetentionJours() { return dureeRetentionJours; }
    public void setDureeRetentionJours(Integer dureeRetentionJours) { this.dureeRetentionJours = dureeRetentionJours; }
    public String getNumeroAgrement() { return numeroAgrement; }
    public void setNumeroAgrement(String numeroAgrement) { this.numeroAgrement = numeroAgrement; }
    public String getCarteProfessionnelle() { return carteProfessionnelle; }
    public void setCarteProfessionnelle(String carteProfessionnelle) { this.carteProfessionnelle = carteProfessionnelle; }
    public String getCaisseGarantie() { return caisseGarantie; }
    public void setCaisseGarantie(String caisseGarantie) { this.caisseGarantie = caisseGarantie; }
    public String getAssuranceRc() { return assuranceRc; }
    public void setAssuranceRc(String assuranceRc) { this.assuranceRc = assuranceRc; }
    public LocalDate getDateAgrement() { return dateAgrement; }
    public void setDateAgrement(LocalDate dateAgrement) { this.dateAgrement = dateAgrement; }
    public LocalDate getDateExpirationAgrement() { return dateExpirationAgrement; }
    public void setDateExpirationAgrement(LocalDate d) { this.dateExpirationAgrement = d; }
    public String getTypeActivite() { return typeActivite; }
    public void setTypeActivite(String typeActivite) { this.typeActivite = typeActivite; }
    public String getZonesIntervention() { return zonesIntervention; }
    public void setZonesIntervention(String zonesIntervention) { this.zonesIntervention = zonesIntervention; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getLogoFileKey() { return logoFileKey; }
    public void setLogoFileKey(String logoFileKey) { this.logoFileKey = logoFileKey; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
    public String getCouleurPrimaire() { return couleurPrimaire; }
    public void setCouleurPrimaire(String couleurPrimaire) { this.couleurPrimaire = couleurPrimaire; }
    public String getCouleurSecondaire() { return couleurSecondaire; }
    public void setCouleurSecondaire(String couleurSecondaire) { this.couleurSecondaire = couleurSecondaire; }
    public String getLangueDefaut() { return langueDefaut; }
    public void setLangueDefaut(String langueDefaut) { this.langueDefaut = langueDefaut; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public String getFuseauHoraire() { return fuseauHoraire; }
    public void setFuseauHoraire(String fuseauHoraire) { this.fuseauHoraire = fuseauHoraire; }
    public String getFormatDate() { return formatDate; }
    public void setFormatDate(String formatDate) { this.formatDate = formatDate; }
    public String getMentionsLegales() { return mentionsLegales; }
    public void setMentionsLegales(String mentionsLegales) { this.mentionsLegales = mentionsLegales; }
    public String getPlanAbonnement() { return planAbonnement; }
    public void setPlanAbonnement(String planAbonnement) { this.planAbonnement = planAbonnement; }
    public Integer getMaxUtilisateurs() { return maxUtilisateurs; }
    public void setMaxUtilisateurs(Integer maxUtilisateurs) { this.maxUtilisateurs = maxUtilisateurs; }
    public Integer getMaxBiens() { return maxBiens; }
    public void setMaxBiens(Integer maxBiens) { this.maxBiens = maxBiens; }
    public Integer getMaxContacts() { return maxContacts; }
    public void setMaxContacts(Integer maxContacts) { this.maxContacts = maxContacts; }
    public Integer getMaxProjets() { return maxProjets; }
    public void setMaxProjets(Integer maxProjets) { this.maxProjets = maxProjets; }
    public LocalDate getDateDebutAbonnement() { return dateDebutAbonnement; }
    public void setDateDebutAbonnement(LocalDate d) { this.dateDebutAbonnement = d; }
    public LocalDate getDateFinAbonnement() { return dateFinAbonnement; }
    public void setDateFinAbonnement(LocalDate d) { this.dateFinAbonnement = d; }
    public boolean isPeriodeEssai() { return periodeEssai; }
    public void setPeriodeEssai(boolean periodeEssai) { this.periodeEssai = periodeEssai; }
    public java.math.BigDecimal getCaMensuelCible() { return caMensuelCible; }
    public void setCaMensuelCible(java.math.BigDecimal caMensuelCible) { this.caMensuelCible = caMensuelCible; }
    public Integer getVentesMensuelCible() { return ventesMensuelCible; }
    public void setVentesMensuelCible(Integer ventesMensuelCible) { this.ventesMensuelCible = ventesMensuelCible; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Instant getDateSuspension() { return dateSuspension; }
    public void setDateSuspension(Instant dateSuspension) { this.dateSuspension = dateSuspension; }
    public String getRaisonSuspension() { return raisonSuspension; }
    public void setRaisonSuspension(String raisonSuspension) { this.raisonSuspension = raisonSuspension; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public String getNotesInternes() { return notesInternes; }
    public void setNotesInternes(String notesInternes) { this.notesInternes = notesInternes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
