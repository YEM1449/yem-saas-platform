package com.yem.hlm.backend.task.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 300) String title,
        String description,
        LocalDateTime dueDate,
        UUID assigneeId,
        UUID contactId,
        UUID propertyId
) {}
