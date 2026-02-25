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
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
    private final TenantRepository tenantRepository;

    public ProjectService(ProjectRepository projectRepository, TenantRepository tenantRepository) {
        this.projectRepository = projectRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Creates a new project for the current tenant.
     *
     * @throws ProjectNameAlreadyExistsException if name is already used by this tenant
     */
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        if (projectRepository.existsByTenant_IdAndName(tenantId, request.name())) {
            throw new ProjectNameAlreadyExistsException(request.name());
        }

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        var project = new Project(tenant, request.name());
        project.setDescription(request.description());

        project = projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    /**
     * Lists all projects for the current tenant, ordered by name.
     */
    public List<ProjectResponse> listAll() {
        UUID tenantId = TenantContext.getTenantId();
        return projectRepository.findByTenant_IdOrderByNameAsc(tenantId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Lists active projects for the current tenant, ordered by name.
     */
    public List<ProjectResponse> listActive() {
        UUID tenantId = TenantContext.getTenantId();
        return projectRepository.findByTenant_IdAndStatusOrderByNameAsc(tenantId, ProjectStatus.ACTIVE)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Gets a project by ID (tenant-scoped).
     *
     * @throws ProjectNotFoundException if project not found or belongs to another tenant
     */
    public ProjectResponse getById(UUID projectId) {
        UUID tenantId = TenantContext.getTenantId();
        var project = projectRepository.findByTenant_IdAndId(tenantId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return ProjectResponse.from(project);
    }

    /**
     * Updates a project (partial update — null fields are ignored).
     *
     * @throws ProjectNotFoundException if project not found
     * @throws ProjectNameAlreadyExistsException if the new name is already taken
     */
    @Transactional
    public ProjectResponse update(UUID projectId, ProjectUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        var project = projectRepository.findByTenant_IdAndId(tenantId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (request.name() != null && !request.name().equals(project.getName())) {
            if (projectRepository.existsByTenant_IdAndNameAndIdNot(tenantId, request.name(), projectId)) {
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
     * The DB FK constraint prevents physical deletion when properties still reference this project.
     *
     * @throws ProjectNotFoundException if project not found
     */
    @Transactional
    public void archive(UUID projectId) {
        UUID tenantId = TenantContext.getTenantId();
        var project = projectRepository.findByTenant_IdAndId(tenantId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        project.setStatus(ProjectStatus.ARCHIVED);
        projectRepository.save(project);
    }

    /**
     * Computes aggregated KPIs for a single project.
     *
     * @param projectId the project UUID
     * @return ProjectKpiDTO with all aggregated metrics
     * @throws ProjectNotFoundException if project not found
     */
    public ProjectKpiDTO getKpis(UUID projectId) {
        UUID tenantId = TenantContext.getTenantId();

        var project = projectRepository.findByTenant_IdAndId(tenantId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        // Total properties
        long total = projectRepository.countTotalProperties(tenantId, projectId);

        // By type
        Map<String, Long> byType = new HashMap<>();
        projectRepository.countPropertiesByType(tenantId, projectId)
                .forEach(row -> byType.put(((PropertyType) row[0]).name(), (Long) row[1]));

        // By status
        Map<String, Long> byStatus = new HashMap<>();
        projectRepository.countPropertiesByStatus(tenantId, projectId)
                .forEach(row -> byStatus.put(((PropertyStatus) row[0]).name(), (Long) row[1]));

        // Deposit stats
        long depositsCount = 0;
        BigDecimal depositsTotalAmount = BigDecimal.ZERO;
        List<Object[]> dStats = projectRepository.depositStats(tenantId, projectId);
        if (!dStats.isEmpty()) {
            Object[] row = dStats.get(0);
            depositsCount = (Long) row[0];
            depositsTotalAmount = toBigDecimal(row[1]);
        }

        // Sales (CONFIRMED) stats
        long salesCount = 0;
        BigDecimal salesTotalAmount = BigDecimal.ZERO;
        List<Object[]> sStats = projectRepository.salesStats(tenantId, projectId, DepositStatus.CONFIRMED);
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
