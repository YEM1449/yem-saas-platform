package com.yem.hlm.backend.project.api;

import com.yem.hlm.backend.project.api.dto.ProjectCreateRequest;
import com.yem.hlm.backend.project.api.dto.ProjectKpiDTO;
import com.yem.hlm.backend.project.api.dto.ProjectResponse;
import com.yem.hlm.backend.project.api.dto.ProjectUpdateRequest;
import com.yem.hlm.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Project CRUD and KPI operations.
 * <p>
 * RBAC rules (same as PropertyController):
 * - ADMIN / MANAGER : create, read, update, archive
 * - AGENT           : read-only (list, get, kpis)
 */
@Tag(name = "Projects", description = "Real-estate project master management")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Create a new project.
     * Requires ADMIN or MANAGER role.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    /**
     * List all projects for the current tenant, ordered by name.
     * All authenticated roles can read.
     *
     * @param activeOnly if true, returns only ACTIVE projects (default false)
     */
    @GetMapping
    public List<ProjectResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        return activeOnly ? projectService.listActive() : projectService.listAll();
    }

    /**
     * Get a single project by ID.
     * All authenticated roles can read.
     */
    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable UUID id) {
        return projectService.getById(id);
    }

    /**
     * Update a project (partial — null fields are ignored).
     * Requires ADMIN or MANAGER role.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        return projectService.update(id, request);
    }

    /**
     * Archive a project (sets status = ARCHIVED).
     * The DB FK constraint prevents physical deletion when properties still reference this project.
     * Requires ADMIN role.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        projectService.archive(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get aggregated KPIs for a single project.
     * Requires ADMIN or MANAGER role.
     *
     * @param id the project UUID
     * @return ProjectKpiDTO with property counts, status breakdown, deposits and sales aggregates
     */
    @GetMapping("/{id}/kpis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectKpiDTO getKpis(@PathVariable UUID id) {
        return projectService.getKpis(id);
    }

    @PostMapping("/{id}/logo")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProjectResponse> uploadLogo(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(projectService.uploadLogo(id, file));
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<InputStreamResource> downloadLogo(@PathVariable UUID id) throws IOException {
        String contentType = projectService.getLogoContentType(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(new InputStreamResource(projectService.downloadLogo(id)));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteLogo(@PathVariable UUID id) throws IOException {
        projectService.deleteLogo(id);
        return ResponseEntity.noContent().build();
    }
}
