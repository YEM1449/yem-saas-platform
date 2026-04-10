package com.yem.hlm.backend.task.service;

import com.yem.hlm.backend.task.api.dto.CreateTaskRequest;
import com.yem.hlm.backend.task.api.dto.DueTaskDto;
import com.yem.hlm.backend.task.api.dto.TaskResponse;
import com.yem.hlm.backend.task.api.dto.UpdateTaskRequest;
import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;
import com.yem.hlm.backend.task.repo.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskResponse create(UUID societeId, UUID createdByUserId, CreateTaskRequest req) {
        UUID assigneeId = req.assigneeId() != null ? req.assigneeId() : createdByUserId;
        Task task = new Task(societeId, assigneeId, createdByUserId, req.title());
        task.setDescription(req.description());
        task.setDueDate(req.dueDate());
        task.setContactId(req.contactId());
        task.setPropertyId(req.propertyId());
        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse getById(UUID societeId, UUID taskId) {
        return TaskResponse.from(requireTask(societeId, taskId));
    }

    public Page<TaskResponse> listByAssignee(UUID societeId, UUID assigneeId, Pageable pageable) {
        return taskRepository.findBySocieteIdAndAssigneeIdOrderByDueDateAsc(societeId, assigneeId, pageable)
                .map(TaskResponse::from);
    }

    public Page<TaskResponse> listByStatus(UUID societeId, TaskStatus status, Pageable pageable) {
        return taskRepository.findBySocieteIdAndStatusOrderByDueDateAsc(societeId, status, pageable)
                .map(TaskResponse::from);
    }

    public List<TaskResponse> listByContact(UUID societeId, UUID contactId) {
        return taskRepository.findBySocieteIdAndContactIdOrderByCreatedAtDesc(societeId, contactId)
                .stream().map(TaskResponse::from).toList();
    }

    public List<TaskResponse> listByProperty(UUID societeId, UUID propertyId) {
        return taskRepository.findBySocieteIdAndPropertyIdOrderByCreatedAtDesc(societeId, propertyId)
                .stream().map(TaskResponse::from).toList();
    }

    @Transactional
    public TaskResponse update(UUID societeId, UUID taskId, UpdateTaskRequest req) {
        Task task = requireTask(societeId, taskId);
        if (req.title() != null)      task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.dueDate() != null)    task.setDueDate(req.dueDate());
        if (req.assigneeId() != null) task.setAssigneeId(req.assigneeId());
        if (req.status() != null) {
            task.setStatus(req.status());
            if (req.status() == TaskStatus.DONE && task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
            } else if (req.status() != TaskStatus.DONE) {
                task.setCompletedAt(null);
            }
        }
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(UUID societeId, UUID taskId) {
        Task task = requireTask(societeId, taskId);
        taskRepository.delete(task);
    }

    /**
     * Returns tasks that are overdue or due within the next 24 hours for the given assignee.
     * Used by the frontend notification polling service.
     */
    public List<DueTaskDto> listDueNow(UUID societeId, UUID assigneeId) {
        LocalDateTime horizon = LocalDateTime.now().plusHours(24);
        return taskRepository
                .findUrgent(societeId, assigneeId, horizon,
                        org.springframework.data.domain.PageRequest.of(0, 50))
                .stream()
                .map(DueTaskDto::from)
                .toList();
    }

    private Task requireTask(UUID societeId, UUID taskId) {
        return taskRepository.findBySocieteIdAndId(societeId, taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
