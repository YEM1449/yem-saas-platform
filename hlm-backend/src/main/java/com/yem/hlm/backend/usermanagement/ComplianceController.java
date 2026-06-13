package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.api.dto.ComplianceInfoResponse;
import com.yem.hlm.backend.societe.api.dto.UpdateComplianceRequest;
import com.yem.hlm.backend.societe.domain.Societe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Self-service CNDP compliance endpoint for société ADMIN (B-005).
 *
 * <p>Lets an ADMIN record their société's CNDP declaration number without needing
 * SUPER_ADMIN access. The number is then exposed on the buyer portal legal pages.
 *
 * <p>SUPER_ADMIN can still manage these fields via {@code PATCH /api/admin/societes/{id}}.
 */
@Tag(name = "Mon espace — Conformité", description = "Gestion CNDP (Loi 09-08)")
@RestController
@RequestMapping("/api/mon-espace/compliance")
@PreAuthorize("hasRole('ADMIN')")
public class ComplianceController {

    private final SocieteRepository   societeRepository;
    private final SocieteContextHelper societeCtx;

    public ComplianceController(SocieteRepository societeRepository,
                                SocieteContextHelper societeCtx) {
        this.societeRepository = societeRepository;
        this.societeCtx        = societeCtx;
    }

    @Operation(summary = "Get CNDP compliance info for my société")
    @GetMapping
    @Transactional(readOnly = true)
    public ComplianceInfoResponse getCompliance() {
        return ComplianceInfoResponse.from(currentSociete());
    }

    @Operation(summary = "Update CNDP declaration number and date for my société (B-005)")
    @PatchMapping
    @Transactional
    public ComplianceInfoResponse updateCompliance(@RequestBody UpdateComplianceRequest request) {
        Societe societe = currentSociete();
        if (request.numeroCndp() != null) {
            societe.setNumeroCndp(request.numeroCndp());
        }
        if (request.dateDeclarationCndp() != null) {
            societe.setDateDeclarationCndp(request.dateDeclarationCndp());
        }
        return ComplianceInfoResponse.from(societeRepository.save(societe));
    }

    private Societe currentSociete() {
        UUID societeId = societeCtx.requireSocieteId();
        return societeRepository.findById(societeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Société non trouvée : " + societeId));
    }
}
