package com.yem.hlm.backend.task.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "task",
    indexes = {
        @Index(name = "idx_task_societe_assignee", columnList = "societe_id,assignee_id"),
        @Index(name = "idx_task_societe_status",   columnList = "societe_id,status"),
        @Index(name = "idx_task_societe_due_date", columnList = "societe_id,due_date"),
        @Index(name = "idx_task_societe_contact",  columnList = "societe_id,contact_id"),
        @Index(name = "idx_task_societe_property", columnList = "societe_id,property_id")
    }
)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Setter
    @Column(name = "assignee_id", nullable = false)
    private UUID assigneeId;

    @Setter
    @Column(name = "contact_id")
    private UUID contactId;

    @Setter
    @Column(name = "property_id")
    private UUID propertyId;

    @Setter
    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Setter
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Setter
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = TaskStatus.OPEN;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Task(UUID societeId, UUID assigneeId, UUID createdBy, String title) {
        this.societeId  = societeId;
        this.assigneeId = assigneeId;
        this.createdBy  = createdBy;
        this.title      = title;
        this.status     = TaskStatus.OPEN;
    }
}
