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
