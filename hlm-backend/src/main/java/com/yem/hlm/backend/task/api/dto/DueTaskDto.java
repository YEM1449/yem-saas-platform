package com.yem.hlm.backend.task.api.dto;

import com.yem.hlm.backend.task.domain.Task;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lightweight projection returned by {@code GET /api/tasks/due-now}. */
public record DueTaskDto(UUID id, String title, LocalDateTime dueDate) {

    public static DueTaskDto from(Task t) {
        return new DueTaskDto(t.getId(), t.getTitle(), t.getDueDate());
    }
}
