package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that:
 * <ol>
 *   <li>Only ROLE_ADMIN can access user management endpoints (RBAC).</li>
 *   <li>A member of société A is not visible from société B (isolation).</li>
 * </ol>
 *
 * NOTE: No @Transactional — AuditEventListener uses Propagation.REQUIRES_NEW which
 * opens a separate connection that cannot see uncommitted test data → FK violation.
 * Use per-test UID suffixes on all email addresses to avoid uk_user_email collisions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserManagementIsolationIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;

    private String adminBearerA;
    private String managerBearerA;
    private String agentBearerA;
    private String adminBearerB;

    private User targetUser;
    private Societe societeA;
    private Societe societeB;

    @BeforeEach
    void setup() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        societeA = societeRepository.save(new Societe("Société Alpha", "MA"));
        societeB = societeRepository.save(new Societe("Société Beta", "MA"));

        // Admin of société A
        User adminA = userRepository.save(new User("admin-a-" + uid + "@test.local", "hash"));
        appUserSocieteRepository.save(membership(adminA, societeA, "ADMIN"));

        // Manager and Agent of société A
        User managerA = userRepository.save(new User("mgr-a-" + uid + "@test.local", "hash"));
        appUserSocieteRepository.save(membership(managerA, societeA, "MANAGER"));

        User agentA = userRepository.save(new User("agent-a-" + uid + "@test.local", "hash"));
        appUserSocieteRepository.save(membership(agentA, societeA, "AGENT"));

        // Admin of société B
        User adminB = userRepository.save(new User("admin-b-" + uid + "@test.local", "hash"));
        appUserSocieteRepository.save(membership(adminB, societeB, "ADMIN"));

        // A regular member in société A that we will try to fetch
        targetUser = userRepository.save(new User("target-" + uid + "@test.local", "hash"));
        appUserSocieteRepository.save(membership(targetUser, societeA, "AGENT"));

        adminBearerA   = "Bearer " + jwtProvider.generate(adminA.getId(),   societeA.getId(), UserRole.ROLE_ADMIN);
        managerBearerA = "Bearer " + jwtProvider.generate(managerA.getId(), societeA.getId(), UserRole.ROLE_MANAGER);
        agentBearerA   = "Bearer " + jwtProvider.generate(agentA.getId(),   societeA.getId(), UserRole.ROLE_AGENT);
        adminBearerB   = "Bearer " + jwtProvider.generate(adminB.getId(),   societeB.getId(), UserRole.ROLE_ADMIN);
    }

    // ── RBAC ──────────────────────────────────────────────────────────────────

    @Test
    void listeUtilisateurs_sansToken_retourne401() throws Exception {
        mvc.perform(get("/api/mon-espace/utilisateurs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listeUtilisateurs_avecRoleManager_retourne200() throws Exception {
        // MANAGER can view team members per the permission matrix
        mvc.perform(get("/api/mon-espace/utilisateurs")
                        .header("Authorization", managerBearerA))
                .andExpect(status().isOk());
    }

    @Test
    void listeUtilisateurs_avecRoleAgent_retourne403() throws Exception {
        // AGENT cannot view team members
        mvc.perform(get("/api/mon-espace/utilisateurs")
                        .header("Authorization", agentBearerA))
                .andExpect(status().isForbidden());
    }

    @Test
    void listeUtilisateurs_avecRoleAdmin_retourne200() throws Exception {
        mvc.perform(get("/api/mon-espace/utilisateurs")
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk());
    }

    // ── Cross-société isolation ────────────────────────────────────────────────

    @Test
    void detailUtilisateur_membreSocieteA_nonVisibleDepuisSocieteB() throws Exception {
        // Admin of société B tries to fetch a member of société A → 404
        mvc.perform(get("/api/mon-espace/utilisateurs/{userId}", targetUser.getId())
                        .header("Authorization", adminBearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailUtilisateur_membreSocieteA_visibleDepuisAdminSocieteA() throws Exception {
        mvc.perform(get("/api/mon-espace/utilisateurs/{userId}", targetUser.getId())
                        .header("Authorization", adminBearerA))
                .andExpect(status().isOk());
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
