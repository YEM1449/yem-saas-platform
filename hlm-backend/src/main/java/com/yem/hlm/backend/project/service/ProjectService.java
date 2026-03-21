package com.yem.hlm.backend.project.service;

import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.project.api.dto.ProjectCreateRequest;
import com.yem.hlm.backend.project.api.dto.ProjectKpiDTO;
import com.yem.hlm.backend.project.api.dto.ProjectResponse;
import com.yem.hlm.backend.project.api.dto.ProjectUpdateRequest;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for Project CRUD operations and project-level KPI aggregation.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a new project for the current société.
     *
     * @throws ProjectNameAlreadyExistsException if name is already used by this société
     */
    @Transactional
    @CacheEvict(value = CacheConfig.PROJECTS_CACHE, allEntries = true)
    public ProjectResponse create(ProjectCreateRequest request) {
        UUID societeId = SocieteContext.getSocieteId();

        if (projectRepository.existsBySocieteIdAndName(societeId, request.name())) {
            throw new ProjectNameAlreadyExistsException(request.name());
        }

        var project = new Project(societeId, request.name());
        project.setDescription(request.description());

        project = projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    /**
     * Lists all projects for the current société, ordered by name.
     */
    @Cacheable(value = CacheConfig.PROJECTS_CACHE, key = "'all:' + T(com.yem.hlm.backend.societe.SocieteContext).getSocieteId()")
    public List<ProjectResponse> listAll() {
        UUID societeId = SocieteContext.getSocieteId();
        return projectRepository.findBySocieteIdOrderByNameAsc(societeId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Lists active projects for the current société, ordered by name.
     */
    @Cacheable(value = CacheConfig.PROJECTS_CACHE, key = "'active:' + T(com.yem.hlm.backend.societe.SocieteContext).getSocieteId()")
    public List<ProjectResponse> listActive() {
        UUID societeId = SocieteContext.getSocieteId();
        return projectRepository.findBySocieteIdAndStatusOrderByNameAsc(societeId, ProjectStatus.ACTIVE)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Gets a project by ID (société-scoped).
     *
     * @throws ProjectNotFoundException if project not found or belongs to another société
     */
    public ProjectResponse getById(UUID projectId) {
        UUID societeId = SocieteContext.getSocieteId();
        var project = projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return ProjectResponse.from(project);
    }

    /**
     * Updates a project (partial update — null fields are ignored).
     */
    @Transactional
    @CacheEvict(value = CacheConfig.PROJECTS_CACHE, allEntries = true)
    public ProjectResponse update(UUID projectId, ProjectUpdateRequest request) {
        UUID societeId = SocieteContext.getSocieteId();

        var project = projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (request.name() != null && !request.name().equals(project.getName())) {
            if (projectRepository.existsBySocieteIdAndNameAndIdNot(societeId, request.name(), projectId)) {
                throw new ProjectNameAlreadyExistsException(request.name());
            }
            project.setName(request.name());
        }
        if (request.description() != null) project.setDescription(request.description());
        if (request.status() != null) project.setStatus(request.status());

        project = projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    /**
     * Archives a project (sets status to ARCHIVED).
     */
    @Transactional
    @CacheEvict(value = CacheConfig.PROJECTS_CACHE, allEntries = true)
    public void archive(UUID projectId) {
        UUID societeId = SocieteContext.getSocieteId();
        var project = projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        project.setStatus(ProjectStatus.ARCHIVED);
        projectRepository.save(project);
    }

    /**
     * Computes aggregated KPIs for a single project.
     */
    public ProjectKpiDTO getKpis(UUID projectId) {
        UUID societeId = SocieteContext.getSocieteId();

        var project = projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        // Total properties
        long total = projectRepository.countTotalProperties(societeId, projectId);

        // By type
        Map<String, Long> byType = new HashMap<>();
        projectRepository.countPropertiesByType(societeId, projectId)
                .forEach(row -> byType.put(((PropertyType) row[0]).name(), (Long) row[1]));

        // By status
        Map<String, Long> byStatus = new HashMap<>();
        projectRepository.countPropertiesByStatus(societeId, projectId)
                .forEach(row -> byStatus.put(((PropertyStatus) row[0]).name(), (Long) row[1]));

        // Deposit stats
        long depositsCount = 0;
        BigDecimal depositsTotalAmount = BigDecimal.ZERO;
        List<Object[]> dStats = projectRepository.depositStats(societeId, projectId);
        if (!dStats.isEmpty()) {
            Object[] row = dStats.get(0);
            depositsCount = (Long) row[0];
            depositsTotalAmount = toBigDecimal(row[1]);
        }

        // Sales (CONFIRMED) stats
        long salesCount = 0;
        BigDecimal salesTotalAmount = BigDecimal.ZERO;
        List<Object[]> sStats = projectRepository.salesStats(societeId, projectId, DepositStatus.CONFIRMED);
        if (!sStats.isEmpty()) {
            Object[] row = sStats.get(0);
            salesCount = (Long) row[0];
            salesTotalAmount = toBigDecimal(row[1]);
        }

        return new ProjectKpiDTO(
                project.getId(),
                project.getName(),
                total,
                byType,
                byStatus,
                depositsCount,
                depositsTotalAmount,
                salesCount,
                salesTotalAmount
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
