package com.yem.hlm.backend.usermanagement.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_project_access")
public class UserProjectAccess {

    @EmbeddedId
    private UserProjectAccessId id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    protected UserProjectAccess() {}

    public UserProjectAccess(UUID userId, UUID projectId, UUID societeId) {
        this.id        = new UserProjectAccessId(userId, projectId);
        this.societeId = societeId;
        this.grantedAt = LocalDateTime.now();
    }

    public UserProjectAccessId getId() { return id; }
    public UUID getUserId()            { return id.getUserId(); }
    public UUID getProjectId()         { return id.getProjectId(); }
    public UUID getSocieteId()         { return societeId; }
    public LocalDateTime getGrantedAt(){ return grantedAt; }
}
