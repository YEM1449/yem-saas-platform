package com.yem.hlm.backend.project.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Project groups one or more Properties under a named real-estate development
 * within a Tenant's portfolio (e.g. "Résidence Les Acacias", "Tour Marina").
 * <p>
 * Each Property MUST belong to exactly one Project (mandatory FK).
 * Deletion of a project is prevented at DB level if properties exist (FK RESTRICT).
 * Use {@link ProjectStatus#ARCHIVED} to retire a project instead.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "project",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project_tenant_name", columnNames = {"tenant_id", "name"})
        },
        indexes = {
                @Index(name = "idx_project_tenant_id", columnList = "tenant_id,id"),
                @Index(name = "idx_project_tenant_status", columnList = "tenant_id,status")
        }
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_project_tenant"))
    private Tenant tenant;

    @Setter
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ProjectStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Creates a new active project.
     *
     * @param tenant the owning tenant
     * @param name   the project name (unique per tenant)
     */
    public Project(Tenant tenant, String name) {
        this.tenant = tenant;
        this.name = name;
        this.status = ProjectStatus.ACTIVE;
    }
}
