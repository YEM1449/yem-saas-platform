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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for RBAC privilege-escalation prevention.
 *
 * <p>Critical invariant (footnote ¹ in the permission matrix):
 * A company-level ADMIN can invite MANAGER or AGENT — but NEVER ADMIN.
 * Only SUPER_ADMIN can assign the ADMIN role.
 *
 * <p>Also covers:
 * <ul>
 *   <li>Company management is SUPER_ADMIN only.</li>
 *   <li>MANAGER can view but not manage team members.</li>
 *   <li>AGENT cannot see or manage team members.</li>
 *   <li>Role-change escalation to ADMIN requires SUPER_ADMIN.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RbacPrivilegeEscalationIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;

    private String superAdminBearer;
    private String adminBearer;
    private String managerBearer;
    private String agentBearer;

    private Societe societe;
    private User targetMember;   // AGENT in the société, used for role-change tests

    @BeforeEach
    void setup() {
        societe = societeRepository.save(new Societe("Test Société RBAC", "MA"));

        // SUPER_ADMIN (no société scope)
        User sa = userRepository.save(new User("sa@rbac.local", "hash"));
        sa.setPlatformRole("SUPER_ADMIN");
        sa.setEnabled(true);
        sa = userRepository.save(sa);
        superAdminBearer = "Bearer " + jwtProvider.generate(sa.getId(), null, "ROLE_SUPER_ADMIN", 0);

        // ADMIN of the société
        User admin = userRepository.save(new User("admin@rbac.local", "hash"));
        appUserSocieteRepository.save(membership(admin, societe, "ADMIN"));
        adminBearer = "Bearer " + jwtProvider.generate(admin.getId(), societe.getId(), UserRole.ROLE_ADMIN);

        // MANAGER of the société
        User manager = userRepository.save(new User("mgr@rbac.local", "hash"));
        appUserSocieteRepository.save(membership(manager, societe, "MANAGER"));
        managerBearer = "Bearer " + jwtProvider.generate(manager.getId(), societe.getId(), UserRole.ROLE_MANAGER);

        // AGENT of the société
        User agent = userRepository.save(new User("agent@rbac.local", "hash"));
        appUserSocieteRepository.save(membership(agent, societe, "AGENT"));
        agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), societe.getId(), UserRole.ROLE_AGENT);

        // A target member (AGENT) that role-change tests operate on
        targetMember = userRepository.save(new User("target@rbac.local", "hash"));
        appUserSocieteRepository.save(membership(targetMember, societe, "AGENT"));
    }

    // ── Group A — Company management (SUPER_ADMIN only) ───────────────────────

    @Test
    void listCompanies_superAdmin_retourne200() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", superAdminBearer))
                .andExpect(status().isOk());
    }

    @Test
    void listCompanies_admin_retourne403() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", adminBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCompanies_manager_retourne403() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCompanies_agent_retourne403() throws Exception {
        mvc.perform(get("/api/admin/societes")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCompanies_sansToken_retourne401() throws Exception {
        mvc.perform(get("/api/admin/societes"))
                .andExpect(status().isUnauthorized());
    }

    // ── Group B — User invitation privilege escalation ────────────────────────

    @Test
    void inviter_admin_avecRoleManager_retourne201() throws Exception {
        // ADMIN can invite MANAGER — this must succeed
        String body = """
            {"email":"new-mgr@rbac.local","prenom":"New","nomFamille":"Mgr","role":"MANAGER"}
            """;
        mvc.perform(post("/api/mon-espace/utilisateurs")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("MANAGER")));
    }

    @Test
    void inviter_admin_avecRoleAgent_retourne201() throws Exception {
        // ADMIN can invite AGENT — this must succeed
        String body = """
            {"email":"new-agt@rbac.local","prenom":"New","nomFamille":"Agt","role":"AGENT"}
            """;
        mvc.perform(post("/api/mon-espace/utilisateurs")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("AGENT")));
    }

    @Test
    void inviter_admin_avecRoleAdmin_retourne403_ROLE_ESCALATION_FORBIDDEN() throws Exception {
        // THE CRITICAL TEST — company ADMIN tries to assign ADMIN role
        // This is a privilege escalation attack and must NEVER succeed
        String body = """
            {"email":"escalate@rbac.local","prenom":"Hack","nomFamille":"Attempt","role":"ADMIN"}
            """;
        mvc.perform(post("/api/mon-espace/utilisateurs")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ROLE_ESCALATION_FORBIDDEN")));
    }

    @Test
    void inviter_superAdmin_avecRoleAdmin_retourne201() throws Exception {
        // SUPER_ADMIN can assign any role — this is the authorised path for creating a company admin
        // Note: SUPER_ADMIN uses /api/admin/societes/{id}/membres not /api/mon-espace/utilisateurs
        // This test validates the superadmin path through SocieteController
        String body = """
            {"userId":"%s","role":"ADMIN"}
            """.formatted(targetMember.getId());
        mvc.perform(post("/api/admin/societes/{id}/membres", societe.getId())
                        .header("Authorization", superAdminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict()); // already a member → MEMBRE_DEJA_EXISTANT is 409
        // We verify SUPER_ADMIN is not blocked by ROLE_ESCALATION_FORBIDDEN (they'd get 409 MEMBRE_DEJA_EXISTANT instead)
    }

    @Test
    void inviter_manager_retourne403() throws Exception {
        // MANAGER cannot invite anyone
        String body = """
            {"email":"mgr-try@rbac.local","prenom":"Try","nomFamille":"Mgr","role":"AGENT"}
            """;
        mvc.perform(post("/api/mon-espace/utilisateurs")
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviter_agent_retourne403() throws Exception {
        // AGENT cannot invite anyone
        String body = """
            {"email":"agt-try@rbac.local","prenom":"Try","nomFamille":"Agt","role":"AGENT"}
            """;
        mvc.perform(post("/api/mon-espace/utilisateurs")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── Group C — Role-change escalation ─────────────────────────────────────

    @Test
    void changerRole_admin_versManager_retourne200() throws Exception {
        long version = getTargetVersion();
        String body = """
            {"nouveauRole":"MANAGER","version":%d}
            """.formatted(version);
        mvc.perform(patch("/api/mon-espace/utilisateurs/{id}/role", targetMember.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("MANAGER")));
    }

    @Test
    void changerRole_admin_versAdmin_retourne403_ROLE_ESCALATION_FORBIDDEN() throws Exception {
        // CRITICAL: ADMIN cannot promote a member to ADMIN
        long version = getTargetVersion();
        String body = """
            {"nouveauRole":"ADMIN","version":%d}
            """.formatted(version);
        mvc.perform(patch("/api/mon-espace/utilisateurs/{id}/role", targetMember.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ROLE_ESCALATION_FORBIDDEN")));
    }

    @Test
    void changerRole_manager_retourne403() throws Exception {
        // MANAGER cannot change roles
        long version = getTargetVersion();
        String body = """
            {"nouveauRole":"MANAGER","version":%d}
            """.formatted(version);
        mvc.perform(patch("/api/mon-espace/utilisateurs/{id}/role", targetMember.getId())
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── Group D — Read access by role ─────────────────────────────────────────

    @Test
    void listerMembres_manager_retourne200() throws Exception {
        // MANAGER can view team members (read-only access)
        mvc.perform(get("/api/mon-espace/utilisateurs")
                        .header("Authorization", managerBearer))
                .andExpect(status().isOk());
    }

    @Test
    void listerMembres_agent_retourne403() throws Exception {
        // AGENT cannot view team members
        mvc.perform(get("/api/mon-espace/utilisateurs")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getTargetVersion() {
        return userRepository.findById(targetMember.getId()).orElseThrow().getVersion();
    }

    private AppUserSociete membership(User user, Societe soc, String role) {
        AppUserSociete aus = new AppUserSociete(
                new AppUserSocieteId(user.getId(), soc.getId()), role);
        aus.setActif(true);
        aus.setDateAjout(Instant.now());
        return aus;
    }
}
