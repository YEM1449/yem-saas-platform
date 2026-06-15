package com.yem.hlm.backend.visite.api;

import com.yem.hlm.backend.visite.api.dto.AnnulerVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.CompteRenduRequest;
import com.yem.hlm.backend.visite.api.dto.CreateVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.UpdateVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.VisiteResponse;
import com.yem.hlm.backend.visite.domain.StatutVisite;
import com.yem.hlm.backend.visite.service.VisiteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API for the Visites module (agenda, prise de RDV, comptes-rendus).
 *
 * <p>All endpoints are société-scoped via {@code SocieteContext}. AGENT has write access so
 * agents run their own visites; the service restricts AGENT to their own data (RG-V04).
 * {@code from}/{@code to} are ISO-8601 instants; the frontend sends Casablanca-derived bounds.
 */
@Tag(name = "Visites", description = "Visites commerciales — agenda, prise de RDV, comptes-rendus")
@RestController
@RequestMapping("/api/visites")
public class VisiteController {

    private final VisiteService visiteService;

    public VisiteController(VisiteService visiteService) {
        this.visiteService = visiteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public List<VisiteResponse> agenda(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) StatutVisite statut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return visiteService.agenda(agentId, statut, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse getById(@PathVariable UUID id) {
        return visiteService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<VisiteResponse> create(@Valid @RequestBody CreateVisiteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visiteService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVisiteRequest req) {
        return visiteService.update(id, req);
    }

    @PostMapping("/{id}/confirmer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse confirmer(@PathVariable UUID id) {
        return visiteService.confirmer(id);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse noShow(@PathVariable UUID id) {
        return visiteService.marquerNoShow(id);
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse annuler(@PathVariable UUID id, @Valid @RequestBody AnnulerVisiteRequest req) {
        return visiteService.annuler(id, req.raison());
    }

    @PostMapping("/{id}/compte-rendu")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VisiteResponse compteRendu(@PathVariable UUID id, @Valid @RequestBody CompteRenduRequest req) {
        return visiteService.enregistrerCompteRendu(id, req);
    }
}
