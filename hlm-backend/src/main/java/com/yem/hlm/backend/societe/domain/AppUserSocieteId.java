package com.yem.hlm.backend.societe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AppUserSocieteId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "societe_id")
    private UUID societeId;

    protected AppUserSocieteId() {}

    public AppUserSocieteId(UUID userId, UUID societeId) {
        this.userId = userId;
        this.societeId = societeId;
    }

    public UUID getUserId() { return userId; }
    public UUID getSocieteId() { return societeId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUserSocieteId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(societeId, that.societeId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, societeId); }
}
