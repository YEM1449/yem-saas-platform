package com.yem.hlm.backend.tranche.api;

import com.yem.hlm.backend.tranche.api.dto.*;
import com.yem.hlm.backend.tranche.service.ProjectGenerationService;
import com.yem.hlm.backend.tranche.service.TrancheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Tranche (phased delivery group) management
 * and for the bulk project generation wizard endpoint.
 */
@Tag(name = "Tranches", description = "Phased delivery groups and bulk project generation")
@RestController
@RequestMapping("/api")
public class TrancheController {

    private final TrancheService           trancheService;
    private final ProjectGenerationService generationService;

    public TrancheController(TrancheService trancheService,
                              ProjectGenerationService generationService) {
        this.trancheService    = trancheService;
        this.generationService = generationService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generation endpoint (wizard)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/projects/generate
     * <p>
     * Runs the 5-step wizard generation: creates the project, all tranches,
     * all buildings (Immeubles), and all property units in one transaction.
     * Returns a detailed summary of everything that was created.
     */
    @Operation(summary = "Generate project with tranches, buildings and units (wizard)")
    @PostMapping("/projects/generate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ProjectGenerationResponse> generate(
            @Valid @RequestBody ProjectGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(generationService.generate(request));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tranche CRUD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/projects/{projectId}/tranches
     * Lists all tranches for a project, ordered by numero, with KPI aggregates.
     */
    @Operation(summary = "List tranches for a project")
    @GetMapping("/projects/{projectId}/tranches")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public List<TrancheDto> list(@PathVariable UUID projectId) {
        return trancheService.listByProject(projectId);
    }

    /**
     * GET /api/projects/{projectId}/tranches/{trancheId}
     * Get a single tranche with KPI aggregates.
     */
    @Operation(summary = "Get a single tranche with KPIs")
    @GetMapping("/projects/{projectId}/tranches/{trancheId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public TrancheDto getOne(@PathVariable UUID projectId,
                              @PathVariable UUID trancheId) {
        return trancheService.getById(projectId, trancheId);
    }

    /**
     * PATCH /api/projects/{projectId}/tranches/{trancheId}/statut
     * Advance the tranche statut (forward-only state machine).
     */
    @Operation(summary = "Advance tranche statut")
    @PatchMapping("/projects/{projectId}/tranches/{trancheId}/statut")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public TrancheDto advanceStatut(@PathVariable UUID projectId,
                                     @PathVariable UUID trancheId,
                                     @Valid @RequestBody UpdateTrancheStatutRequest req) {
        return trancheService.advanceStatut(projectId, trancheId, req);
    }
}
