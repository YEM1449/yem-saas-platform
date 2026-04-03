package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.portal.api.dto.PortalVenteResponse;
import com.yem.hlm.backend.portal.service.PortalVenteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Buyer portal vente endpoints — ROLE_PORTAL only.
 *
 * <p>Buyers can view their own vente pipeline data:
 * status, payment milestones, and documents.
 */
@Tag(name = "Portal \u2013 Ventes", description = "Buyer portal sale pipeline endpoints (ROLE_PORTAL)")
@RestController
@RequestMapping("/api/portal/ventes")
@PreAuthorize("hasRole('PORTAL')")
public class PortalVenteController {

    private final PortalVenteService portalVenteService;

    public PortalVenteController(PortalVenteService portalVenteService) {
        this.portalVenteService = portalVenteService;
    }

    /** Returns all ventes for the authenticated buyer. */
    @GetMapping
    public List<PortalVenteResponse> list() {
        return portalVenteService.listMyVentes();
    }

    /** Returns a specific vente for the authenticated buyer. */
    @GetMapping("/{id}")
    public PortalVenteResponse get(@PathVariable UUID id) {
        return portalVenteService.getMyVente(id);
    }
}
