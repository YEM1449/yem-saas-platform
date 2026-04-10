package com.yem.hlm.backend.task.repo;

import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /** Count open tasks (OPEN + IN_PROGRESS) for the assignee. */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.societeId  = :societeId
              AND t.assigneeId = :assigneeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
            """)
    long countOpenByAssignee(@Param("societeId") UUID societeId,
                             @Param("assigneeId") UUID assigneeId);

    /** Count overdue tasks: open/in-progress with dueDate before now. */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.societeId  = :societeId
              AND t.assigneeId = :assigneeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.dueDate IS NOT NULL
              AND t.dueDate < :now
            """)
    long countOverdueByAssignee(@Param("societeId") UUID societeId,
                                @Param("assigneeId") UUID assigneeId,
                                @Param("now") LocalDateTime now);

    /** Count tasks due today (between start-of-day and end-of-day). */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.societeId  = :societeId
              AND t.assigneeId = :assigneeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.dueDate IS NOT NULL
              AND t.dueDate >= :start
              AND t.dueDate < :end
            """)
    long countDueTodayByAssignee(@Param("societeId") UUID societeId,
                                 @Param("assigneeId") UUID assigneeId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /** Urgent tasks: overdue or due today, ordered by dueDate ASC. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.societeId  = :societeId
              AND t.assigneeId = :assigneeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.dueDate IS NOT NULL
              AND t.dueDate < :horizon
            ORDER BY t.dueDate ASC
            """)
    List<Task> findUrgent(@Param("societeId") UUID societeId,
                          @Param("assigneeId") UUID assigneeId,
                          @Param("horizon") LocalDateTime horizon,
                          Pageable pageable);

    /** Societe-wide open task count — for ADMIN/MANAGER overview. */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.societeId = :societeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
            """)
    long countOpenBySociete(@Param("societeId") UUID societeId);

    /** Societe-wide overdue task count. */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.societeId = :societeId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.dueDate IS NOT NULL
              AND t.dueDate < :now
            """)
    long countOverdueBySociete(@Param("societeId") UUID societeId,
                               @Param("now") LocalDateTime now);
}
