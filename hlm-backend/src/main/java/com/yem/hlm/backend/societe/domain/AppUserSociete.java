package com.yem.hlm.backend.societe.domain;

import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_user_societe")
public class AppUserSociete {

    @EmbeddedId
    private AppUserSocieteId id;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "date_ajout", nullable = false)
    private Instant dateAjout = Instant.now();

    @Column(name = "date_retrait")
    private Instant dateRetrait;

    @Column(name = "raison_retrait", columnDefinition = "TEXT")
    private String raisonRetrait;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ajoute_par")
    private User ajoutePar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retire_par")
    private User retirePar;

    @Column(name = "notifications_actives", nullable = false)
    private boolean notificationsActives = true;

    protected AppUserSociete() {}

    public AppUserSociete(AppUserSocieteId id, String role) {
        this.id = id;
        this.role = role;
    }

    public AppUserSocieteId getId() { return id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Instant getDateAjout() { return dateAjout; }
    public void setDateAjout(Instant dateAjout) { this.dateAjout = dateAjout; }
    public Instant getDateRetrait() { return dateRetrait; }
    public void setDateRetrait(Instant dateRetrait) { this.dateRetrait = dateRetrait; }
    public String getRaisonRetrait() { return raisonRetrait; }
    public void setRaisonRetrait(String raisonRetrait) { this.raisonRetrait = raisonRetrait; }
    public User getAjoutePar() { return ajoutePar; }
    public void setAjoutePar(User ajoutePar) { this.ajoutePar = ajoutePar; }
    public User getRetirePar() { return retirePar; }
    public void setRetirePar(User retirePar) { this.retirePar = retirePar; }
    public boolean isNotificationsActives() { return notificationsActives; }
    public void setNotificationsActives(boolean notificationsActives) { this.notificationsActives = notificationsActives; }

    public java.util.UUID getUserId() { return id.getUserId(); }
    public java.util.UUID getSocieteId() { return id.getSocieteId(); }
}
