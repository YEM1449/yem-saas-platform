package com.yem.hlm.backend.societe.api;

import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteService;
import com.yem.hlm.backend.societe.annotation.RequiresSuperAdmin;
import com.yem.hlm.backend.societe.api.dto.*;
import com.yem.hlm.backend.usermanagement.InvitationService;
import com.yem.hlm.backend.usermanagement.dto.InviterUtilisateurRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * SUPER_ADMIN endpoints for company management.
 * All operations require {@code @RequiresSuperAdmin} — enforced by {@link com.yem.hlm.backend.societe.SuperAdminAspect}.
 */
@Tag(name = "Societe Admin", description = "Gestion des sociétés — SUPER_ADMIN uniquement")
@RestController
@RequestMapping("/api/admin/societes")
public class SocieteController {

    private final SocieteService societeService;
    private final InvitationService invitationService;

    public SocieteController(SocieteService societeService, InvitationService invitationService) {
        this.societeService = societeService;
        this.invitationService = invitationService;
    }

    // ── List / Read ───────────────────────────────────────────────────────────

    /** Paginated, filterable list of all companies. */
    @GetMapping
    @RequiresSuperAdmin
    public ResponseEntity<Page<SocieteDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String pays,
            @RequestParam(required = false) String planAbonnement,
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        SocieteFilter filter = new SocieteFilter(search, pays, planAbonnement, actif);
        return ResponseEntity.ok(societeService.listSocietes(filter, pageable));
    }

    /** Full company details including internal fields (SUPER_ADMIN only — R9). */
    @GetMapping("/{id}")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDetailDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(societeService.getDetail(id));
    }

    /** Operational statistics (member counts, quota usage, etc.). */
    @GetMapping("/{id}/stats")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteStatsDto> stats(@PathVariable UUID id) {
        return ResponseEntity.ok(societeService.getStats(id));
    }

    /** RGPD compliance score + missing-field breakdown. */
    @GetMapping("/{id}/compliance")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteComplianceDto> compliance(@PathVariable UUID id) {
        return ResponseEntity.ok(societeService.getCompliance(id));
    }

    // ── Create / Update ───────────────────────────────────────────────────────

    @Operation(
        summary = "Créer une nouvelle société",
        description = "Crée une nouvelle Société. Requiert le rôle SUPER_ADMIN. " +
                      "Les ADMIN de société ne peuvent pas appeler cet endpoint."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Société créée"),
        @ApiResponse(responseCode = "400", description = "Erreur de validation — champs requis manquants"),
        @ApiResponse(responseCode = "401", description = "Authentification requise — JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle SUPER_ADMIN requis",
            content = @Content(schema = @Schema(implementation = com.yem.hlm.backend.common.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Nom de société déjà existant (SOCIETE_ALREADY_EXISTS)",
            content = @Content(schema = @Schema(implementation = com.yem.hlm.backend.common.error.ErrorResponse.class)))
    })
    @PostMapping
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDetailDto> create(@Valid @RequestBody CreateSocieteRequest req) {
        UUID actorId = SocieteContext.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(societeService.createSociete(req, actorId));
    }

    @PutMapping("/{id}")
    @RequiresSuperAdmin
    public ResponseEntity<SocieteDetailDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateSocieteRequest req) {
        UUID actorId = SocieteContext.getUserId();
        return ResponseEntity.ok(societeService.updateSociete(id, req, actorId));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Suspend a company. R6: all member JWTs are revoked atomically. */
    @PostMapping("/{id}/desactiver")
    @RequiresSuperAdmin
    public ResponseEntity<Void> desactiver(@PathVariable UUID id,
                                            @Valid @RequestBody DesactiverRequest req) {
        UUID actorId = SocieteContext.getUserId();
        societeService.desactiver(id, req, actorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactiver")
    @RequiresSuperAdmin
    public ResponseEntity<Void> reactiver(@PathVariable UUID id) {
        UUID actorId = SocieteContext.getUserId();
        societeService.reactiver(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/membres")
    @RequiresSuperAdmin
    public ResponseEntity<List<MembreSocieteDto>> listMembres(@PathVariable UUID id) {
        return ResponseEntity.ok(societeService.listMembres(id));
    }

    @PostMapping("/{id}/membres")
    @RequiresSuperAdmin
    public ResponseEntity<MembreSocieteDto> addMembre(@PathVariable UUID id,
                                                       @Valid @RequestBody AddMembreRequest req) {
        UUID actorId = SocieteContext.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(societeService.addMembre(id, req, actorId));
    }

    /**
     * Invite a new or existing user to a société by email.
     * Creates the user account if it doesn't exist, sends an activation email, and
     * creates the AppUserSociete membership. SUPER_ADMIN can assign any role including ADMIN.
     */
    @PostMapping("/{id}/invite")
    @RequiresSuperAdmin
    public ResponseEntity<Void> inviteUser(@PathVariable UUID id,
                                           @Valid @RequestBody InviterUtilisateurRequest req) {
        UUID actorId = SocieteContext.getUserId();
        invitationService.inviter(req, id, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}/membres/{userId}/role")
    @RequiresSuperAdmin
    public ResponseEntity<MembreSocieteDto> updateMembreRole(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMembreRoleRequest req) {
        UUID actorId = SocieteContext.getUserId();
        return ResponseEntity.ok(societeService.updateMembreRole(id, userId, req, actorId));
    }

    @DeleteMapping("/{id}/membres/{userId}")
    @RequiresSuperAdmin
    public ResponseEntity<Void> removeMembre(@PathVariable UUID id,
                                              @PathVariable UUID userId) {
        UUID actorId = SocieteContext.getUserId();
        societeService.removeMembre(id, userId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ── Impersonation (SA-7) ─────────────────────────────────────────────────

    /**
     * Issues a short-lived impersonation JWT for the target user in the given société.
     * The SUPER_ADMIN must be authenticated; the issued token inherits the target
     * user's role and carries an {@code imp} claim for audit traceability.
     */
    @PostMapping("/{id}/impersonate/{userId}")
    @RequiresSuperAdmin
    public ResponseEntity<ImpersonateResponse> impersonate(@PathVariable UUID id,
                                                            @PathVariable UUID userId) {
        UUID superAdminId = SocieteContext.getUserId();
        return ResponseEntity.ok(societeService.impersonate(id, userId, superAdminId));
    }
}
