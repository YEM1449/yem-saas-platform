package com.yem.hlm.backend.project.service;

import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared guard for project-status enforcement.
 *
 * <p>Centralises the two rules that multiple services (PropertyService,
 * and future SaleContractService) must enforce:
 * <ol>
 *   <li>The project must exist and belong to the current tenant (→ 404 otherwise).</li>
 *   <li>The project must be {@link ProjectStatus#ACTIVE} (→ 400 ARCHIVED_PROJECT otherwise).</li>
 * </ol>
 *
 * <p>Usage pattern:
 * <pre>{@code
 *   Project project = projectActiveGuard.requireActive(societeId, request.projectId());
 * }</pre>
 */
@Service
public class ProjectActiveGuard {

    private final ProjectRepository projectRepository;

    public ProjectActiveGuard(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Loads a société-scoped project and asserts it is ACTIVE.
     *
     * @param societeId the current société (from SocieteContext)
     * @param projectId the requested project UUID
     * @return the loaded {@link Project} entity
     * @throws ProjectNotFoundException           if the project does not exist or belongs to a different société
     * @throws ArchivedProjectAssignmentException if the project exists but its status is not ACTIVE
     */
    public Project requireActive(UUID societeId, UUID projectId) {
        Project project = projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new ArchivedProjectAssignmentException(projectId);
        }

        return project;
    }
}
