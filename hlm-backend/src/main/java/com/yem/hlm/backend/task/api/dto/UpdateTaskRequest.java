package com.yem.hlm.backend.task.api.dto;

import com.yem.hlm.backend.task.domain.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateTaskRequest(
        @Size(max = 300) String title,
        String description,
        LocalDateTime dueDate,
        UUID assigneeId,
        TaskStatus status
) {}
