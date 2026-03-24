package com.yem.hlm.backend.user.domain;

import com.yem.hlm.backend.societe.domain.AppUserSociete;
import jakarta.persistence.*;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = {"email"})
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Setter
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Platform-level role (nullable — société-specific roles live in AppUserSociete). */
    @Column(name = "platform_role", length = 50)
    private String platformRole;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ── Profil ────────────────────────────────────────────────────────────────
    @Column(name = "prenom", length = 100)
    private String prenom;

    @Column(name = "nom_famille", length = 100)
    private String nomFamille;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "poste", length = 150)
    private String poste;

    @Column(name = "langue_interface", length = 10)
    private String langueInterface;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    // ── Consentement CGU (RGPD Art. 7) ───────────────────────────────────────
    @Column(name = "consentement_cgu", nullable = false)
    private boolean consentementCgu = false;

    @Column(name = "consentement_cgu_date")
    private Instant consentementCguDate;

    @Column(name = "consentement_cgu_version", length = 20)
    private String consentementCguVersion;

    // ── Workflow invitation ───────────────────────────────────────────────────
    @Column(name = "invitation_token", length = 128, unique = true)
    private String invitationToken;

    @Column(name = "invitation_expire_at")
    private Instant invitationExpireAt;

    @Column(name = "invitation_envoyee_at")
    private Instant invitationEnvoyeeAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_par")
    private User invitePar;

    // ── Sécurité compte ───────────────────────────────────────────────────────
    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;

    /** Persistent manual block set by an admin — distinct from the timed lockout via locked_until. */
    @Column(name = "compte_bloque", nullable = false)
    private boolean compteBloque = false;

    @Column(name = "compte_bloque_at")
    private Instant compteBlockeAt;

    // ── Notes internes (organisation — non DCP) ───────────────────────────────
    @Column(name = "notes_admin", columnDefinition = "TEXT")
    private String notesAdmin;

    // ── Optimistic locking ────────────────────────────────────────────────────
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    // ── Société memberships (read-only nav for Specification queries) ─────────
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private List<AppUserSociete> societes;

    protected User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public String getPlatformRole() { return platformRole; }
    public void setPlatformRole(String platformRole) { this.platformRole = platformRole; }
    public int getTokenVersion() { return tokenVersion; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }

    // ── Profil getters/setters ────────────────────────────────────────────────
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getNomFamille() { return nomFamille; }
    public void setNomFamille(String nomFamille) { this.nomFamille = nomFamille; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getPoste() { return poste; }
    public void setPoste(String poste) { this.poste = poste; }
    public String getLangueInterface() { return langueInterface; }
    public void setLangueInterface(String langueInterface) { this.langueInterface = langueInterface; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    // ── CGU getters/setters ───────────────────────────────────────────────────
    public boolean isConsentementCgu() { return consentementCgu; }
    public void setConsentementCgu(boolean consentementCgu) { this.consentementCgu = consentementCgu; }
    public Instant getConsentementCguDate() { return consentementCguDate; }
    public void setConsentementCguDate(Instant consentementCguDate) { this.consentementCguDate = consentementCguDate; }
    public String getConsentementCguVersion() { return consentementCguVersion; }
    public void setConsentementCguVersion(String consentementCguVersion) { this.consentementCguVersion = consentementCguVersion; }

    // ── Invitation getters/setters ────────────────────────────────────────────
    public String getInvitationToken() { return invitationToken; }
    public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }
    public Instant getInvitationExpireAt() { return invitationExpireAt; }
    public void setInvitationExpireAt(Instant invitationExpireAt) { this.invitationExpireAt = invitationExpireAt; }
    public Instant getInvitationEnvoyeeAt() { return invitationEnvoyeeAt; }
    public void setInvitationEnvoyeeAt(Instant invitationEnvoyeeAt) { this.invitationEnvoyeeAt = invitationEnvoyeeAt; }
    public User getInvitePar() { return invitePar; }
    public void setInvitePar(User invitePar) { this.invitePar = invitePar; }

    // ── Sécurité getters/setters ──────────────────────────────────────────────
    public Instant getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(Instant derniereConnexion) { this.derniereConnexion = derniereConnexion; }
    public boolean isCompteBloque() { return compteBloque; }
    public void setCompteBloque(boolean compteBloque) { this.compteBloque = compteBloque; }
    public Instant getCompteBlockeAt() { return compteBlockeAt; }
    public void setCompteBlockeAt(Instant compteBlockeAt) { this.compteBlockeAt = compteBlockeAt; }

    // ── Notes admin getter/setter ─────────────────────────────────────────────
    public String getNotesAdmin() { return notesAdmin; }
    public void setNotesAdmin(String notesAdmin) { this.notesAdmin = notesAdmin; }

    // ── Optimistic locking ────────────────────────────────────────────────────
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    /** Nom complet calculé — sans null checks dupliqués partout. */
    public String getNomComplet() {
        if (prenom != null && nomFamille != null) return prenom + " " + nomFamille;
        if (prenom != null) return prenom;
        if (nomFamille != null) return nomFamille;
        return email;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    /**
     * Returns true if the account is currently locked out.
     */
    public boolean isLockedOut() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Records a failed login attempt. If the number of consecutive failures
     * reaches maxAttempts, the account is locked for lockDurationMinutes.
     * If a previous lockout has already expired, the counter is reset first
     * so an expired lockout does not cause an immediate re-lock on the next failure.
     */
    public void recordFailedAttempt(int maxAttempts, int lockDurationMinutes) {
        if (lockedUntil != null && !Instant.now().isBefore(lockedUntil)) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plusSeconds((long) lockDurationMinutes * 60);
        }
    }

    /**
     * Resets the failed login counter and clears the lockout (called on successful login).
     */
    public void resetLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
