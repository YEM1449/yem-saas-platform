package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.common.dto.PageResponse;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.usermanagement.dto.*;
import io.swagger.v3.oas.annotations.Operation;
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
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final InvitationService invitationService;
    private final UserGdprService userGdprService;

    public UserManagementController(UserManagementService userManagementService,
                                    InvitationService invitationService,
                                    UserGdprService userGdprService) {
        this.userManagementService = userManagementService;
        this.invitationService = invitationService;
        this.userGdprService = userGdprService;
    }

    // ── Lister les membres ─────────────────────────────────────────────────────

    @Operation(summary = "Lister les membres de la société avec filtres et pagination")
    @GetMapping
    public PageResponse<MembreDto> listerMembres(
            MembreFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID societeId = SocieteContext.getSocieteId();
        return userManagementService.listerMembres(societeId, filter, pageable);
    }

    // ── Détail d'un membre ─────────────────────────────────────────────────────

    @Operation(summary = "Obtenir le détail d'un membre")
    @GetMapping("/{userId}")
    public MembreDto getDetail(@PathVariable UUID userId) {
        UUID societeId = SocieteContext.getSocieteId();
        return userManagementService.getDetail(userId, societeId);
    }

    // ── Inviter un utilisateur ─────────────────────────────────────────────────

    @Operation(summary = "Inviter un nouvel utilisateur dans la société")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembreDto inviter(@Valid @RequestBody InviterUtilisateurRequest req) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        var savedUser = invitationService.inviter(req, societeId, adminId);
        return userManagementService.getDetail(savedUser.getId(), societeId);
    }

    // ── Ré-inviter ─────────────────────────────────────────────────────────────

    @Operation(summary = "Renvoyer le lien d'invitation à un utilisateur en attente")
    @PostMapping("/{userId}/reinviter")
    public MembreDto reinviter(@PathVariable UUID userId) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        invitationService.reinviter(userId, societeId, adminId);
        return userManagementService.getDetail(userId, societeId);
    }

    // ── Modifier le profil ─────────────────────────────────────────────────────

    @Operation(summary = "Modifier le profil d'un membre")
    @PatchMapping("/{userId}")
    public MembreDto modifierProfil(@PathVariable UUID userId,
                                    @Valid @RequestBody ModifierUtilisateurRequest req) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        return userManagementService.modifierProfil(userId, societeId, req, adminId);
    }

    // ── Changer le rôle ────────────────────────────────────────────────────────

    @Operation(summary = "Changer le rôle d'un membre dans la société")
    @PatchMapping("/{userId}/role")
    public MembreDto changerRole(@PathVariable UUID userId,
                                 @Valid @RequestBody ChangerRoleRequest req) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        return userManagementService.changerRole(userId, societeId, req, adminId);
    }

    // ── Retirer un membre ──────────────────────────────────────────────────────

    @Operation(summary = "Retirer un membre de la société")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerMembre(@PathVariable UUID userId,
                               @Valid @RequestBody RetirerUtilisateurRequest req) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        userManagementService.retirerMembre(userId, societeId, req, adminId);
    }

    // ── Débloquer un compte ────────────────────────────────────────────────────

    @Operation(summary = "Débloquer manuellement le compte d'un utilisateur")
    @PostMapping("/{userId}/debloquer")
    public MembreDto debloquerCompte(@PathVariable UUID userId) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        return userManagementService.debloquerCompte(userId, societeId, adminId);
    }

    // ── RGPD Art. 15 — Export des données personnelles ────────────────────────

    @Operation(summary = "Exporter les données personnelles d'un utilisateur (RGPD Art. 15)")
    @GetMapping("/{userId}/export-donnees")
    public UserGdprService.UserDataExport exportDonnees(@PathVariable UUID userId) {
        UUID societeId = SocieteContext.getSocieteId();
        return userGdprService.exportUserData(userId, societeId);
    }

    // ── RGPD Art. 17 — Anonymisation ──────────────────────────────────────────

    @Operation(summary = "Anonymiser les données personnelles d'un utilisateur (RGPD Art. 17)")
    @DeleteMapping("/{userId}/anonymiser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anonymiserUtilisateur(@PathVariable UUID userId) {
        UUID societeId = SocieteContext.getSocieteId();
        UUID adminId = SocieteContext.getUserId();
        userGdprService.anonymiserUtilisateur(userId, societeId, adminId);
    }
}
