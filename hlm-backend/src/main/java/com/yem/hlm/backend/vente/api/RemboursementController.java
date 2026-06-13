package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.vente.api.dto.RemboursementRequests.MarquerEffectueRequest;
import com.yem.hlm.backend.vente.api.dto.RemboursementRequests.UpsertRemboursementRequest;
import com.yem.hlm.backend.vente.api.dto.RemboursementResponse;
import com.yem.hlm.backend.vente.service.RemboursementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Refund tracking for a cancelled vente (#028). The {@code DU} record is created automatically
 * on cancellation; these endpoints let a gestionnaire view it, adjust the amount, and mark it paid.
 */
@Tag(name = "Remboursement", description = "Suivi du remboursement du dépôt après annulation (Loi 44-00)")
@RestController
@RequestMapping("/api/ventes/{venteId}/remboursement")
public class RemboursementController {

    private final RemboursementService service;

    public RemboursementController(RemboursementService service) {
        this.service = service;
    }

    @GetMapping
    public RemboursementResponse get(@PathVariable UUID venteId) {
        return RemboursementResponse.from(service.get(venteId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RemboursementResponse upsert(@PathVariable UUID venteId,
                                        @Valid @RequestBody UpsertRemboursementRequest request) {
        return RemboursementResponse.from(service.upsertDu(venteId, request.montant(), request.motif()));
    }

    @PostMapping("/effectue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RemboursementResponse marquerEffectue(@PathVariable UUID venteId,
                                                 @Valid @RequestBody MarquerEffectueRequest request) {
        return RemboursementResponse.from(service.marquerEffectue(
                venteId, request.dateRemboursement(), request.moyen(), request.reference()));
    }
}
