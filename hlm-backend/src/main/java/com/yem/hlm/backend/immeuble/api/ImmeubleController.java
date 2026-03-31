package com.yem.hlm.backend.immeuble.api;

import com.yem.hlm.backend.immeuble.api.dto.CreateImmeubleRequest;
import com.yem.hlm.backend.immeuble.api.dto.ImmeubleResponse;
import com.yem.hlm.backend.immeuble.api.dto.UpdateImmeubleRequest;
import com.yem.hlm.backend.immeuble.service.ImmeubleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Immeubles", description = "Building (Immeuble) management within projects")
@RestController
@RequestMapping("/api/immeubles")
public class ImmeubleController {

    private final ImmeubleService immeubleService;

    public ImmeubleController(ImmeubleService immeubleService) {
        this.immeubleService = immeubleService;
    }

    @Operation(summary = "Create a new building (ADMIN/MANAGER only)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ImmeubleResponse> create(@Valid @RequestBody CreateImmeubleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(immeubleService.create(request));
    }

    @Operation(summary = "Get a building by ID")
    @GetMapping("/{id}")
    public ImmeubleResponse getById(@PathVariable UUID id) {
        return immeubleService.getById(id);
    }

    @Operation(summary = "List buildings with optional project filter")
    @GetMapping
    public List<ImmeubleResponse> list(@RequestParam(required = false) UUID projectId) {
        return immeubleService.list(projectId);
    }

    @Operation(summary = "Update a building (ADMIN/MANAGER only)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ImmeubleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateImmeubleRequest request) {
        return immeubleService.update(id, request);
    }

    @Operation(summary = "Delete a building (ADMIN only)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        immeubleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
