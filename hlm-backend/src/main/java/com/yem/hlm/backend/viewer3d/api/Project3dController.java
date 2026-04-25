package com.yem.hlm.backend.viewer3d.api;

import com.yem.hlm.backend.viewer3d.api.dto.*;
import com.yem.hlm.backend.viewer3d.service.Project3dService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 3D visualisation endpoints — CRM roles (ADMIN, MANAGER, AGENT).
 *
 * <ul>
 *   <li>POST /api/projects/{id}/3d-model                — upload/replace GLB metadata (ADMIN/MANAGER)</li>
 *   <li>GET  /api/projects/{id}/3d-model                — fetch model + pre-signed URL + mappings</li>
 *   <li>PUT  /api/projects/{id}/3d-model/mappings       — bulk upsert (ADMIN only)</li>
 *   <li>GET  /api/projects/{id}/3d-properties-status    — lightweight colour snapshot (10 s cache)</li>
 * </ul>
 */
@Tag(name = "3D Visualiseur", description = "Project 3D model management and lot status overlay")
@RestController
public class Project3dController {

    private final Project3dService service;

    public Project3dController(Project3dService service) {
        this.service = service;
    }

    @Operation(summary = "Request a pre-signed PUT URL for direct GLB upload to R2 (step 1 of 2)")
    @PostMapping("/api/projects/{projetId}/3d-model/upload-url")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public UploadUrlResponse generateUploadUrl(
            @PathVariable UUID projetId,
            @Valid @RequestBody UploadUrlRequest req) throws IOException {
        return service.generateUploadUrl(projetId, req);
    }

    @Operation(summary = "Confirm GLB upload and store model metadata (step 2 of 2)")
    @PostMapping("/api/projects/{projetId}/3d-model")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Project3dModelResponse> upsert(
            @PathVariable UUID projetId,
            @Valid @RequestBody Create3dModelRequest req) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upsertModel(projetId, req));
    }

    @Operation(summary = "Get 3D model with pre-signed URL and full mesh→lot mappings")
    @GetMapping("/api/projects/{projetId}/3d-model")
    public Project3dModelResponse getModel(@PathVariable UUID projetId) throws IOException {
        return service.getModel(projetId);
    }

    @Operation(summary = "Bulk upsert mesh → lot mappings (replaces all existing mappings for this project)")
    @PutMapping("/api/projects/{projetId}/3d-model/mappings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkUpsertMappings(
            @PathVariable UUID projetId,
            @Valid @RequestBody BulkMappingRequest req) {
        service.bulkUpsertMappings(projetId, req);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lightweight lot status snapshot for colour-coding (10 s Caffeine cache)")
    @GetMapping("/api/projects/{projetId}/3d-properties-status")
    public List<Lot3dStatusDto> getStatusSnapshot(@PathVariable UUID projetId) {
        return service.getStatusSnapshot(projetId);
    }
}
