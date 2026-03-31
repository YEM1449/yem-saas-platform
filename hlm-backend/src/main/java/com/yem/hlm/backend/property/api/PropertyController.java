package com.yem.hlm.backend.property.api;

import com.yem.hlm.backend.property.api.dto.ImportResultResponse;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.service.PropertyImportService;
import com.yem.hlm.backend.property.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Property CRUD operations.
 * <p>
 * RBAC Rules:
 * - ADMIN: Full CRUD access (create, read, update, delete)
 * - MANAGER: Can create, read, update (but not delete)
 * - AGENT: Can only read (list and get details)
 */
@Tag(name = "Properties", description = "Property inventory CRUD and CSV import")
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyImportService importService;

    public PropertyController(PropertyService propertyService, PropertyImportService importService) {
        this.propertyService = propertyService;
        this.importService = importService;
    }

    /**
     * Create a new property.
     * Requires ADMIN or MANAGER role.
     *
     * @param request the property creation request
     * @return 201 CREATED with PropertyResponse
     */
    @Operation(summary = "Create a new property (ADMIN/MANAGER only)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyCreateRequest request) {
        PropertyResponse response = propertyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get property by ID.
     * All authenticated users can read.
     *
     * @param id the property ID
     * @return 200 OK with PropertyResponse, or 404 NOT_FOUND
     */
    @Operation(summary = "Get a property by ID")
    @GetMapping("/{id}")
    public PropertyResponse getById(@PathVariable UUID id) {
        return propertyService.getById(id);
    }

    /**
     * List all non-deleted properties for the current tenant with optional filtering.
     * All authenticated users can read.
     *
     * @param type optional property type filter
     * @param status optional property status filter
     * @return 200 OK with list of PropertyResponse
     */
    @Operation(summary = "List all non-deleted properties with optional filters")
    @GetMapping
    public List<PropertyResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID immeubleId,
            @RequestParam(required = false) PropertyType type,
            @RequestParam(required = false) PropertyStatus status
    ) {
        return propertyService.listAll(projectId, immeubleId, type, status);
    }

    /**
     * Update an existing property.
     * Requires ADMIN or MANAGER role.
     *
     * @param id the property ID
     * @param request the update request
     * @return 200 OK with updated PropertyResponse, or 404 NOT_FOUND
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PropertyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PropertyUpdateRequest request
    ) {
        return propertyService.update(id, request);
    }

    /**
     * Soft delete a property.
     * Requires ADMIN role only.
     *
     * @param id the property ID
     * @return 204 NO_CONTENT, or 404 NOT_FOUND
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        propertyService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ImportResultResponse> importCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        ImportResultResponse result = importService.importCsv(file);
        HttpStatus status = result.errors().isEmpty() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
