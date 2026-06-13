package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.vente.api.dto.DossierFinancementResponse;
import com.yem.hlm.backend.vente.api.dto.UpsertDossierFinancementRequest;
import com.yem.hlm.backend.vente.service.DossierFinancementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Financing file (dossier de financement) of a vente. */
@Tag(name = "Dossier financement", description = "Per-sale financing file and status")
@RestController
@RequestMapping("/api/ventes/{venteId}/dossier-financement")
public class DossierFinancementController {

    private final DossierFinancementService service;

    public DossierFinancementController(DossierFinancementService service) {
        this.service = service;
    }

    @GetMapping
    public DossierFinancementResponse get(@PathVariable UUID venteId) {
        return service.getByVente(venteId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public DossierFinancementResponse upsert(@PathVariable UUID venteId,
                                             @Valid @RequestBody UpsertDossierFinancementRequest request) {
        return service.upsert(venteId, request);
    }
}
