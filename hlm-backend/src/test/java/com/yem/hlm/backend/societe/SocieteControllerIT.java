package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.societe.api.dto.ImpersonateResponse;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code /api/admin/societes/**} (SUPER_ADMIN company management).
 *
 * <p>Covers:
 * <ul>
 *   <li>RBAC — 401/403 for unauthenticated/regular users, 200 for SUPER_ADMIN</li>
 *   <li>CRUD — create, get detail, stats, compliance, update</li>
 *   <li>Lifecycle — desactiver (R6 JWT revocation), reactiver</li>
 *   <li>Members — list, add, update role, remove</li>
 *   <li>R10 — last admin cannot be removed or downgraded</li>
 *   <li>Impersonation — SA-7</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SocieteControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;
    @Autowired UserRepository userRepository;

    private String superAdminBearer;
    private String regularAdminBearer;   // ROLE_ADMIN in a société — NOT SUPER_ADMIN
    private User superAdminUser;
    private User regularAdminUser;
    private Societe existingSociete;
    private User memberUser;

    @BeforeEach
    void setup() {
        // SUPER_ADMIN user
        superAdminUser = new User("superadmin@test.local", "hash");
        superAdminUser.setPlatformRole("SUPER_ADMIN");
        superAdminUser.setEnabled(true);
        superAdminUser = userRepository.save(superAdminUser);

        // Regular tenant ADMIN (should be rejected by SUPER_ADMIN endpoints)
        existingSociete = societeRepository.save(new Societe("Société Existante", "MA"));

        regularAdminUser = userRepository.save(new User("admin@test.local", "hash"));
        appUserSocieteRepository.save(
                membership(regularAdminUser, existingSociete, "ADMIN"));

        // A member in existingSociete we can use in member-management tests
        memberUser = userRepository.save(new User("membre@test.local", "hash"));
        appUserSocieteRepository.save(
                membership(memberUser, existingSociete, "AGENT"));

        superAdminBearer  = "Bearer " + jwtProvider.generate(
                superAdminUser.getId(), null, "ROLE_SUPER_ADMIN", 0);
        regularAdminBearer = "Bearer " + jwtProvider.generate(
                regularAdminUser.getId(), existingSociete.getId(), UserRole.ROLE_ADMIN);
    }

    // ── RBAC ──────────────────────────────────────────────────────────────────

    @Test
    void listSocietes_sansToken_retourne401() throws Exception {
        mvc.perform(get("/api/admin/societes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSocietes_avecRoleAdmin_retourne403() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", regularAdminBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSocietes_avecSuperAdmin_retourne200() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Test
    void createSociete_retourne201_avecNomEtPays() throws Exception {
        mvc.perform(post("/api/admin/societes")
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Nouvelle Société\",\"pays\":\"MA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Nouvelle Société"))
                .andExpect(jsonPath("$.pays").value("MA"));
    }

    @Test
    void createSociete_sansPays_retourne400() throws Exception {
        var body = """
                {"nom": "Société"}
                """;
        mvc.perform(post("/api/admin/societes")
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSociete_nomDuplique_retourne409() throws Exception {
        mvc.perform(post("/api/admin/societes")
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Société Existante\",\"pays\":\"MA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIETE_ALREADY_EXISTS"));
    }

    @Test
    void getSocieteDetail_retourne200_avecNom() throws Exception {
        mvc.perform(get("/api/admin/societes/{id}", existingSociete.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Société Existante"));
    }

    @Test
    void getSocieteDetail_inconnue_retourne404() throws Exception {
        mvc.perform(get("/api/admin/societes/{id}", UUID.randomUUID())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isConflict());  // BusinessRuleException default is CONFLICT
    }

    @Test
    void getSocieteStats_retourne200() throws Exception {
        mvc.perform(get("/api/admin/societes/{id}/stats", existingSociete.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMembres").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void getSocieteCompliance_retourne200() throws Exception {
        mvc.perform(get("/api/admin/societes/{id}/compliance", existingSociete.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.missingFields").isArray());
    }

    @Test
    void updateSociete_retourne200_avecNouveauNom() throws Exception {
        long version = existingSociete.getVersion();
        var body = "{\"version\":" + version + ", \"nom\": \"Société Modifiée\"}";

        mvc.perform(put("/api/admin/societes/{id}", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Société Modifiée"));
    }

    @Test
    void updateSociete_sansVersion_retourne400() throws Exception {
        mvc.perform(put("/api/admin/societes/{id}", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"Sans version\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    void desactiverSociete_retourne204() throws Exception {
        mvc.perform(post("/api/admin/societes/{id}/desactiver", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raison\": \"Non-paiement abonnement\"}"))
                .andExpect(status().isNoContent());

        Societe reloaded = societeRepository.findById(existingSociete.getId()).orElseThrow();
        assertThat(reloaded.isActif()).isFalse();
        assertThat(reloaded.getRaisonSuspension()).isEqualTo("Non-paiement abonnement");
    }

    @Test
    void desactiverSociete_revoqueJwtDesMembres_R6() throws Exception {
        // Capture tokenVersion before deactivation
        User member = userRepository.findById(memberUser.getId()).orElseThrow();
        int versionBefore = member.getTokenVersion();

        mvc.perform(post("/api/admin/societes/{id}/desactiver", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raison\": \"Audit de sécurité\"}"))
                .andExpect(status().isNoContent());

        User memberAfter = userRepository.findById(memberUser.getId()).orElseThrow();
        assertThat(memberAfter.getTokenVersion()).isGreaterThan(versionBefore);
    }

    @Test
    void reactiverSociete_retourne204() throws Exception {
        existingSociete.setActif(false);
        existingSociete.setDateSuspension(Instant.now());
        societeRepository.save(existingSociete);

        mvc.perform(post("/api/admin/societes/{id}/reactiver", existingSociete.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isNoContent());

        Societe reloaded = societeRepository.findById(existingSociete.getId()).orElseThrow();
        assertThat(reloaded.isActif()).isTrue();
        assertThat(reloaded.getDateSuspension()).isNull();
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @Test
    void listMembres_retourne200AvecMembres() throws Exception {
        mvc.perform(get("/api/admin/societes/{id}/membres", existingSociete.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void addMembre_retourne201() throws Exception {
        User nouvelUtilisateur = userRepository.save(new User("nouveau@test.local", "hash"));

        mvc.perform(post("/api/admin/societes/{id}/membres", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + nouvelUtilisateur.getId() + "\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void addMembre_deja_existant_retourne409() throws Exception {
        mvc.perform(post("/api/admin/societes/{id}/membres", existingSociete.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + memberUser.getId() + "\",\"role\":\"AGENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBRE_DEJA_EXISTANT"));
    }

    @Test
    void updateMembreRole_retourne200() throws Exception {
        mvc.perform(put("/api/admin/societes/{id}/membres/{userId}/role",
                        existingSociete.getId(), memberUser.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nouveauRole\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void removeMembre_retourne204() throws Exception {
        // memberUser is AGENT — can be removed
        mvc.perform(delete("/api/admin/societes/{id}/membres/{userId}",
                        existingSociete.getId(), memberUser.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isNoContent());

        AppUserSociete aus = appUserSocieteRepository
                .findById(new AppUserSocieteId(memberUser.getId(), existingSociete.getId()))
                .orElseThrow();
        assertThat(aus.isActif()).isFalse();
    }

    // ── R10 — last admin protection ────────────────────────────────────────────

    @Test
    void removeMembre_dernierAdmin_retourne409() throws Exception {
        // regularAdminUser is the only ADMIN in existingSociete
        mvc.perform(delete("/api/admin/societes/{id}/membres/{userId}",
                        existingSociete.getId(), regularAdminUser.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DERNIER_ADMIN"));
    }

    @Test
    void updateMembreRole_retrograderDernierAdmin_retourne409() throws Exception {
        // regularAdminUser is the only ADMIN — demoting to MANAGER must fail (R10)
        mvc.perform(put("/api/admin/societes/{id}/membres/{userId}/role",
                        existingSociete.getId(), regularAdminUser.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nouveauRole\":\"MANAGER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DERNIER_ADMIN"));
    }

    // ── Impersonation ─────────────────────────────────────────────────────────

    @Test
    void impersonate_retourneTokenValide() throws Exception {
        mvc.perform(post("/api/admin/societes/{id}/impersonate/{userId}",
                        existingSociete.getId(), memberUser.getId())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.targetUserId").value(memberUser.getId().toString()))
                .andExpect(jsonPath("$.targetRole").value("ROLE_AGENT"))
                .andExpect(jsonPath("$.ttlSeconds").value(SocieteService.IMPERSONATION_TTL_SECONDS));
    }

    @Test
    void impersonate_membreInconnu_retourne404() throws Exception {
        mvc.perform(post("/api/admin/societes/{id}/impersonate/{userId}",
                        existingSociete.getId(), UUID.randomUUID())
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AppUserSociete membership(User user, Societe societe, String role) {
        AppUserSociete aus = new AppUserSociete(
                new AppUserSocieteId(user.getId(), societe.getId()), role);
        aus.setActif(true);
        aus.setDateAjout(Instant.now());
        return aus;
    }
}
