package com.yem.hlm.backend.task.api.dto;

import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID societeId,
        UUID assigneeId,
        UUID contactId,
        UUID propertyId,
        String title,
        String description,
        LocalDateTime dueDate,
        TaskStatus status,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(), t.getSocieteId(), t.getAssigneeId(),
                t.getContactId(), t.getPropertyId(), t.getTitle(),
                t.getDescription(), t.getDueDate(), t.getStatus(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getCompletedAt()
        );
    }
}
