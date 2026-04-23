package com.yem.hlm.backend.usermanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserProjectAccessId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    protected UserProjectAccessId() {}

    public UserProjectAccessId(UUID userId, UUID projectId) {
        this.userId    = userId;
        this.projectId = projectId;
    }

    public UUID getUserId()    { return userId; }
    public UUID getProjectId() { return projectId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProjectAccessId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(projectId, that.projectId);
    }

    @Override public int hashCode() { return Objects.hash(userId, projectId); }
}
