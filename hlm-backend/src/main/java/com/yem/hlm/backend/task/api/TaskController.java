package com.yem.hlm.backend.task.api;

import com.yem.hlm.backend.task.api.dto.CreateTaskRequest;
import com.yem.hlm.backend.task.api.dto.DueTaskDto;
import com.yem.hlm.backend.task.api.dto.TaskResponse;
import com.yem.hlm.backend.task.api.dto.UpdateTaskRequest;
import com.yem.hlm.backend.task.domain.TaskStatus;
import com.yem.hlm.backend.task.service.TaskService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Task / follow-up management endpoints.
 *
 * <pre>
 * POST   /api/tasks                         ← create task
 * GET    /api/tasks/{id}                    ← get by id
 * PUT    /api/tasks/{id}                    ← update task
 * DELETE /api/tasks/{id}                    ← delete task (ADMIN only)
 * GET    /api/tasks?assigneeId=&page=&size= ← list by assignee
 * GET    /api/tasks?status=&page=&size=     ← list by status
 * GET    /api/tasks/by-contact/{contactId}  ← tasks linked to a contact
 * GET    /api/tasks/by-property/{propertyId}← tasks linked to a property
 * </pre>
 */
@Tag(name = "Tasks", description = "Task and follow-up management")
@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class TaskController {

    private final TaskService taskService;
    private final SocieteContextHelper societeContextHelper;

    public TaskController(TaskService taskService, SocieteContextHelper societeContextHelper) {
        this.taskService = taskService;
        this.societeContextHelper = societeContextHelper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest req) {
        return taskService.create(
                societeContextHelper.requireSocieteId(),
                societeContextHelper.requireUserId(),
                req);
    }

    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable UUID id) {
        return taskService.getById(societeContextHelper.requireSocieteId(), id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.update(societeContextHelper.requireSocieteId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        taskService.delete(societeContextHelper.requireSocieteId(), id);
    }

    @GetMapping
    public Page<TaskResponse> list(
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID societeId = societeContextHelper.requireSocieteId();
        PageRequest pageable = PageRequest.of(page, size);
        if (assigneeId != null) {
            return taskService.listByAssignee(societeId, assigneeId, pageable);
        }
        if (status != null) {
            return taskService.listByStatus(societeId, status, pageable);
        }
        // Default: return tasks for the current user
        return taskService.listByAssignee(societeId, societeContextHelper.requireUserId(), pageable);
    }

    @GetMapping("/by-contact/{contactId}")
    public List<TaskResponse> byContact(@PathVariable UUID contactId) {
        return taskService.listByContact(societeContextHelper.requireSocieteId(), contactId);
    }

    @GetMapping("/by-property/{propertyId}")
    public List<TaskResponse> byProperty(@PathVariable UUID propertyId) {
        return taskService.listByProperty(societeContextHelper.requireSocieteId(), propertyId);
    }

    /**
     * Returns tasks overdue or due within the next 24 h for the current user.
     * Used by the frontend notification polling service (polls every 60 s).
     */
    @GetMapping("/due-now")
    public List<DueTaskDto> dueNow() {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID userId    = societeContextHelper.requireUserId();
        return taskService.listDueNow(societeId, userId);
    }
}
