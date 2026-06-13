package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.api.dto.ReserveLivraisonProjectResponse;
import com.yem.hlm.backend.vente.domain.StatutReserve;
import com.yem.hlm.backend.vente.repo.ReserveLivraisonRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Project-level view of all delivery reserves (réserves de livraison) across
 * every vente linked to properties in a given project (A-003).
 */
@Tag(name = "Réserves livraison", description = "Project-level delivery reserve tracking")
@RestController
@RequestMapping("/api")
public class ReserveLivraisonProjectController {

    private final ReserveLivraisonRepository reserveRepository;
    private final ProjectRepository          projectRepository;
    private final SocieteContextHelper       societeCtx;

    public ReserveLivraisonProjectController(ReserveLivraisonRepository reserveRepository,
                                              ProjectRepository projectRepository,
                                              SocieteContextHelper societeCtx) {
        this.reserveRepository = reserveRepository;
        this.projectRepository = projectRepository;
        this.societeCtx        = societeCtx;
    }

    /**
     * Returns all delivery reserves for ventes whose property belongs to the project.
     * Scoped to the caller's société; returns 404 if the project is not found.
     */
    @GetMapping("/projects/{projectId}/reserves")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<ReserveLivraisonProjectResponse> getProjectReserves(
            @PathVariable UUID projectId) {
        UUID societeId = societeCtx.requireSocieteId();
        projectRepository.findBySocieteIdAndId(societeId, projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return reserveRepository.findByProjectId(societeId, projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ReserveLivraisonProjectResponse toResponse(Object[] r) {
        return new ReserveLivraisonProjectResponse(
                toUuid(r[0]),          // id
                (String)  r[1],        // description
                StatutReserve.valueOf((String) r[2]), // statut
                toDate(r[3]),          // dateConstat
                toDate(r[4]),          // dateLeveePrevue
                toDate(r[5]),          // dateLeveeReelle
                toUuid(r[6]),          // responsableUserId (nullable)
                toUuid(r[7]),          // venteId
                (String)  r[8],        // venteRef
                toUuid(r[9]),          // propertyId
                (String)  r[10]        // propertyRef
        );
    }

    private static UUID toUuid(Object o) {
        if (o == null) return null;
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
