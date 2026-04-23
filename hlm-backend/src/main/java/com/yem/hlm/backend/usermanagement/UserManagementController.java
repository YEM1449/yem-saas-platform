package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.common.dto.PageResponse;
import com.yem.hlm.backend.common.ratelimit.RateLimiterService;
import com.yem.hlm.backend.common.security.SocieteRoleValidator;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.usermanagement.dto.*;
import com.yem.hlm.backend.usermanagement.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "User Management", description = "Gestion des membres d'une société")
@RestController
@RequestMapping("/api/mon-espace/utilisateurs")
// NOTE: No class-level @PreAuthorize — each method declares its own access rule.
// ADMIN is enforced per-method to allow MANAGER read-only access and GDPR export.
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final InvitationService invitationService;
    private final UserGdprService userGdprService;
    private final SocieteRoleValidator roleValidator;
    private final SocieteContextHelper societeContextHelper;
    private final RateLimiterService rateLimiterService;
    private final UserSettingsService userSettingsService;

    public UserManagementController(UserManagementService userManagementService,
                                    InvitationService invitationService,
                                    UserGdprService userGdprService,
                                    SocieteRoleValidator roleValidator,
                                    SocieteContextHelper societeContextHelper,
                                    RateLimiterService rateLimiterService,
                                    UserSettingsService userSettingsService) {
        this.userManagementService = userManagementService;
        this.invitationService = invitationService;
        this.userGdprService = userGdprService;
        this.roleValidator = roleValidator;
        this.societeContextHelper = societeContextHelper;
        this.rateLimiterService = rateLimiterService;
        this.userSettingsService = userSettingsService;
    }

    // ── Lister les membres — ADMIN and MANAGER can see the team ───────────────

    @Operation(summary = "Lister les membres de la société avec filtres et pagination")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public PageResponse<MembreDto> listerMembres(
            MembreFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userManagementService.listerMembres(societeId, filter, pageable);
    }

    // ── Détail d'un membre — ADMIN and MANAGER ─────────────────────────────────

    @Operation(summary = "Obtenir le détail d'un membre")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public MembreDto getDetail(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userManagementService.getDetail(userId, societeId);
    }

    // ── Inviter un utilisateur — ADMIN only (role restriction via validator) ───

    @Operation(
        summary = "Inviter un nouvel utilisateur dans la société",
        description = "Envoie une invitation par e-mail. " +
                      "ADMIN : peut attribuer MANAGER ou AGENT uniquement. " +
                      "SUPER_ADMIN : peut attribuer n'importe quel rôle, y compris ADMIN. " +
                      "Attribuer le rôle ADMIN en tant qu'ADMIN de société retourne 403 ROLE_ESCALATION_FORBIDDEN."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Invitation envoyée"),
        @ApiResponse(responseCode = "400", description = "Erreur de validation (champ manquant ou format invalide)"),
        @ApiResponse(responseCode = "401", description = "Authentification requise"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant OU tentative d'escalade de privilège (ROLE_ESCALATION_FORBIDDEN)",
            content = @Content(schema = @Schema(implementation = com.yem.hlm.backend.common.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Utilisateur déjà membre OU quota atteint")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public MembreDto inviter(@Valid @RequestBody InviterUtilisateurRequest req) {
        // Privilege escalation guard: ADMIN can only assign MANAGER or AGENT
        roleValidator.validateAssignableRole(req.role());
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        rateLimiterService.checkInvitation(adminId.toString());
        var savedUser = invitationService.inviter(req, societeId, adminId);
        return userManagementService.getDetail(savedUser.getId(), societeId);
    }

    // ── Ré-inviter — ADMIN only ─────────────────────────────────────────────────

    @Operation(summary = "Renvoyer le lien d'invitation à un utilisateur en attente")
    @PostMapping("/{userId}/reinviter")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public MembreDto reinviter(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        invitationService.reinviter(userId, societeId, adminId);
        return userManagementService.getDetail(userId, societeId);
    }

    // ── Modifier le profil — ADMIN only ────────────────────────────────────────

    @Operation(summary = "Modifier le profil d'un membre")
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public MembreDto modifierProfil(@PathVariable UUID userId,
                                    @Valid @RequestBody ModifierUtilisateurRequest req) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        return userManagementService.modifierProfil(userId, societeId, req, adminId);
    }

    // ── Changer le rôle — ADMIN only (role restriction via validator) ──────────

    @Operation(
        summary = "Changer le rôle d'un membre dans la société",
        description = "ADMIN : peut changer vers MANAGER ou AGENT uniquement — pas ADMIN. " +
                      "SUPER_ADMIN : peut attribuer n'importe quel rôle. " +
                      "Tentative de promotion vers ADMIN par un ADMIN retourne 403 ROLE_ESCALATION_FORBIDDEN."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rôle mis à jour"),
        @ApiResponse(responseCode = "400", description = "Erreur de validation ou version manquante"),
        @ApiResponse(responseCode = "403", description = "Escalade de privilège (ROLE_ESCALATION_FORBIDDEN) ou rôle insuffisant",
            content = @Content(schema = @Schema(implementation = com.yem.hlm.backend.common.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Version obsolète (CONCURRENT_UPDATE) ou dernier admin (DERNIER_ADMIN)")
    })
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public MembreDto changerRole(@PathVariable UUID userId,
                                 @Valid @RequestBody ChangerRoleRequest req) {
        // Privilege escalation guard: ADMIN cannot promote a member to ADMIN
        roleValidator.validateAssignableRole(req.nouveauRole());
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        return userManagementService.changerRole(userId, societeId, req, adminId);
    }

    // ── Retirer un membre — ADMIN only ─────────────────────────────────────────

    @Operation(summary = "Retirer un membre de la société")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void retirerMembre(@PathVariable UUID userId,
                               @Valid @RequestBody RetirerUtilisateurRequest req) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        userManagementService.retirerMembre(userId, societeId, req, adminId);
    }

    // ── Débloquer un compte — ADMIN only ───────────────────────────────────────

    @Operation(summary = "Débloquer manuellement le compte d'un utilisateur")
    @PostMapping("/{userId}/debloquer")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public MembreDto debloquerCompte(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        return userManagementService.debloquerCompte(userId, societeId, adminId);
    }

    // ── RGPD Art. 15 — Export — ADMIN and MANAGER ────────────────────────────

    @Operation(summary = "Exporter les données personnelles d'un utilisateur (RGPD Art. 15)")
    @GetMapping("/{userId}/export-donnees")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public UserGdprService.UserDataExport exportDonnees(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userGdprService.exportUserData(userId, societeId);
    }

    // ── Objectifs mensuels (quota) — ADMIN only ───────────────────────────────

    @Operation(summary = "Obtenir les objectifs mensuels d'un utilisateur")
    @GetMapping("/{userId}/quota")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserQuotaResponse getQuota(@PathVariable UUID userId,
                                      @RequestParam(defaultValue = "") String month) {
        UUID societeId = societeContextHelper.requireSocieteId();
        String effectiveMonth = month.isBlank()
                ? java.time.YearMonth.now().toString()
                : month;
        return userSettingsService.getQuota(userId, societeId, effectiveMonth);
    }

    @Operation(summary = "Définir ou mettre à jour les objectifs mensuels d'un utilisateur")
    @PutMapping("/{userId}/quota")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserQuotaResponse upsertQuota(@PathVariable UUID userId,
                                         @Valid @RequestBody UserQuotaRequest req) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userSettingsService.upsertQuota(userId, societeId, req);
    }

    // ── Accès projets — ADMIN only ────────────────────────────────────────────

    @Operation(summary = "Obtenir les projets accessibles à un utilisateur (liste vide = accès total)")
    @GetMapping("/{userId}/project-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ProjectAccessResponse getProjectAccess(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userSettingsService.getProjectAccess(userId, societeId);
    }

    @Operation(summary = "Définir les projets accessibles à un utilisateur (liste vide = accès total)")
    @PutMapping("/{userId}/project-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ProjectAccessResponse setProjectAccess(@PathVariable UUID userId,
                                                  @RequestBody ProjectAccessRequest req) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return userSettingsService.setProjectAccess(userId, societeId, req);
    }

    // ── RGPD Art. 17 — Anonymisation — ADMIN only ────────────────────────────

    @Operation(summary = "Anonymiser les données personnelles d'un utilisateur (RGPD Art. 17)")
    @DeleteMapping("/{userId}/anonymiser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void anonymiserUtilisateur(@PathVariable UUID userId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID adminId   = societeContextHelper.requireUserId();
        userGdprService.anonymiserUtilisateur(userId, societeId, adminId);
    }
}
