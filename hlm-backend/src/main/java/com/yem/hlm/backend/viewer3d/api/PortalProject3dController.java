package com.yem.hlm.backend.viewer3d.api;

import com.yem.hlm.backend.societe.CrossSocieteAccessException;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.viewer3d.api.dto.*;
import com.yem.hlm.backend.viewer3d.service.Project3dService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Portal 3D endpoints — ROLE_PORTAL only (read-only, access-checked per contact).
 *
 * <p>A portal user can see the 3D view of a project only if they have at least one
 * vente in a property that belongs to that project. This prevents buyers from
 * browsing projects where they have no dossier.
 */
@Tag(name = "Portal – 3D Visualiseur", description = "Buyer portal 3D view (ROLE_PORTAL, read-only)")
@RestController
@PreAuthorize("hasRole('PORTAL')")
public class PortalProject3dController {

    private final Project3dService service;

    public PortalProject3dController(Project3dService service) {
        this.service = service;
    }

    @Operation(summary = "Get 3D model for buyer portal (read-only; access restricted to buyer's dossier)")
    @GetMapping("/api/portal/projects/{projetId}/3d-model")
    public Project3dModelResponse getModel(@PathVariable UUID projetId) throws IOException {
        UUID contactId = getContactId();
        if (!service.portalUserHasAccess(projetId, contactId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return service.getModel(projetId);
    }

    @Operation(summary = "Lightweight lot status snapshot for buyer portal colour-coding")
    @GetMapping("/api/portal/projects/{projetId}/3d-properties-status")
    public List<Lot3dStatusDto> getStatusSnapshot(@PathVariable UUID projetId) {
        UUID contactId = getContactId();
        if (!service.portalUserHasAccess(projetId, contactId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return service.getStatusSnapshot(projetId);
    }

    private UUID getContactId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UUID)) {
            throw new CrossSocieteAccessException("Invalid portal principal");
        }
        return (UUID) principal;
    }
}
