package com.yem.hlm.backend.societe.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user_societe")
public class AppUserSociete {

    @EmbeddedId
    private AppUserSocieteId id;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

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

    public java.util.UUID getUserId() { return id.getUserId(); }
    public java.util.UUID getSocieteId() { return id.getSocieteId(); }
}
