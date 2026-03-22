# Task 08 — New Task / Follow-Up Management Module

## Priority: NEW FEATURE
## Risk: N/A (additive)
## Effort: 3 hours
## Depends on: Task 02 (uses SocieteContextHelper)

## Problem

Agents have no structured way to track follow-ups, reminders, or to-do items associated with contacts or properties. The current system only has notification-based reminders with no user-created tasks.

## Module Design

Following existing package conventions:

```
com.yem.hlm.backend.task/
├── api/
│   ├── TaskController.java
│   └── dto/
│       ├── CreateTaskRequest.java
│       ├── UpdateTaskRequest.java
│       └── TaskResponse.java
├── domain/
│   ├── Task.java
│   └── TaskStatus.java
├── repo/
│   └── TaskRepository.java
└── service/
    ├── TaskService.java
    └── TaskNotFoundException.java
```

## Files to Create

### 1. Domain: `task/domain/TaskStatus.java`

```java
package com.yem.hlm.backend.task.domain;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELED
}
```

### 2. Domain: `task/domain/Task.java`

```java
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
        @Index(name = "idx_task_societe_status", columnList = "societe_id,status"),
        @Index(name = "idx_task_societe_due_date", columnList = "societe_id,due_date"),
        @Index(name = "idx_task_societe_contact", columnList = "societe_id,contact_id"),
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
        this.societeId = societeId;
        this.assigneeId = assigneeId;
        this.createdBy = createdBy;
        this.title = title;
        this.status = TaskStatus.OPEN;
    }
}
```

### 3. Repository: `task/repo/TaskRepository.java`

```java
package com.yem.hlm.backend.task.repo;

import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findBySocieteIdAndId(UUID societeId, UUID id);

    Page<Task> findBySocieteIdAndAssigneeIdOrderByDueDateAsc(UUID societeId, UUID assigneeId, Pageable pageable);

    Page<Task> findBySocieteIdAndStatusOrderByDueDateAsc(UUID societeId, TaskStatus status, Pageable pageable);

    List<Task> findBySocieteIdAndContactIdOrderByCreatedAtDesc(UUID societeId, UUID contactId);

    List<Task> findBySocieteIdAndPropertyIdOrderByCreatedAtDesc(UUID societeId, UUID propertyId);

    long countBySocieteIdAndAssigneeIdAndStatus(UUID societeId, UUID assigneeId, TaskStatus status);
}
```

### 4. DTOs, Service, Controller, Exception

Follow the exact patterns from ContactController/ContactService. Key points:
- Inject `SocieteContextHelper` (from Task 02)
- Use `helper.requireSocieteId()` and `helper.requireUserId()`
- `CreateTaskRequest`: record with title (required), description, dueDate, contactId, propertyId, assigneeId
- `TaskResponse`: record with all fields
- `TaskService`: CRUD with société scoping
- `TaskController`: `@RequestMapping("/api/tasks")`, roles `ADMIN/MANAGER/AGENT`

### 5. Liquibase Migration: `039-create-task-table.yaml` (or next available number)

```yaml
databaseChangeLog:
  - changeSet:
      id: "039-create-task-table"
      author: "claude-audit"
      changes:
        - createTable:
            tableName: task
            columns:
              - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true } }
              - column: { name: societe_id, type: uuid, constraints: { nullable: false } }
              - column: { name: assignee_id, type: uuid, constraints: { nullable: false } }
              - column: { name: contact_id, type: uuid }
              - column: { name: property_id, type: uuid }
              - column: { name: title, type: varchar(300), constraints: { nullable: false } }
              - column: { name: description, type: text }
              - column: { name: due_date, type: timestamp }
              - column: { name: status, type: varchar(20), constraints: { nullable: false }, defaultValue: "OPEN" }
              - column: { name: created_by, type: uuid, constraints: { nullable: false } }
              - column: { name: created_at, type: timestamp, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamp, constraints: { nullable: false }, defaultValueComputed: now() }
              - column: { name: completed_at, type: timestamp }
        - addForeignKeyConstraint:
            baseTableName: task
            baseColumnNames: societe_id
            referencedTableName: societe
            referencedColumnNames: id
            constraintName: fk_task_societe
        - createIndex: { indexName: idx_task_societe_assignee, tableName: task, columns: [{ column: societe_id }, { column: assignee_id }] }
        - createIndex: { indexName: idx_task_societe_status, tableName: task, columns: [{ column: societe_id }, { column: status }] }
        - createIndex: { indexName: idx_task_societe_due_date, tableName: task, columns: [{ column: societe_id }, { column: due_date }] }
        - createIndex: { indexName: idx_task_societe_contact, tableName: task, columns: [{ column: societe_id }, { column: contact_id }] }
        - createIndex: { indexName: idx_task_societe_property, tableName: task, columns: [{ column: societe_id }, { column: property_id }] }
```

Add to `db.changelog-master.yaml`.

## Tests to Add

Create `hlm-backend/src/test/java/com/yem/hlm/backend/task/TaskControllerIT.java` following the pattern of `ContactControllerIT.java`:
- Create task → verify 201
- Get task → verify société scoping
- Update task status → verify lifecycle
- Cross-société isolation → verify 404

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=TaskControllerIT
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT
```

## Acceptance Criteria

- [ ] Task CRUD endpoints work: POST/GET/PUT/DELETE `/api/tasks`
- [ ] Tasks are scoped by societeId
- [ ] Tasks can be linked to contacts and/or properties
- [ ] Cross-société isolation verified
- [ ] Liquibase migration runs cleanly
- [ ] All existing tests still pass
