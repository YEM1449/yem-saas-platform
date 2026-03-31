package com.yem.hlm.backend.immeuble;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.immeuble.api.dto.CreateImmeubleRequest;
import com.yem.hlm.backend.immeuble.api.dto.ImmeubleResponse;
import com.yem.hlm.backend.immeuble.api.dto.UpdateImmeubleRequest;
import com.yem.hlm.backend.societe.SocieteRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code /api/immeubles}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Auth guards (401/403)</li>
 *   <li>CRUD — create, get, list (with projectId filter), update, delete</li>
 *   <li>Validation — missing required fields, duplicate name within project</li>
 *   <li>RBAC — AGENT cannot create/update/delete; MANAGER can create/update</li>
 *   <li>Cross-société isolation — société B cannot see société A's buildings</li>
 * </ul>
 *
 * <p>No {@code @Transactional} at class level — see CLAUDE.md pitfall notes.
 * UID-based email suffixes prevent unique-constraint collisions across test runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImmeubleIT extends IntegrationTestBase {

    private static final UUID SOCIETE_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;
    private String uid;
    private int counter = 0;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_ADMIN);

        User manager = userRepository.save(new User("mgr-imm-" + uid + "@acme.com", "hashed"));
        managerBearer = "Bearer " + jwtProvider.generate(manager.getId(), SOCIETE_ID, UserRole.ROLE_MANAGER);

        User agent = userRepository.save(new User("agent-imm-" + uid + "@acme.com", "hashed"));
        agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), SOCIETE_ID, UserRole.ROLE_AGENT);
    }

    // =========================================================================
    // Auth guards
    // =========================================================================

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/immeubles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/immeubles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asAgent_returns403() throws Exception {
        UUID projectId = createProject();
        mvc.perform(post("/api/immeubles")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateImmeubleRequest(projectId, "Block A", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asManager_returns403() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse imm = doCreate(adminBearer, projectId, "Delete-RBAC-" + uid);

        mvc.perform(delete("/api/immeubles/{id}", imm.id()).header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asAgent_returns403() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse imm = doCreate(adminBearer, projectId, "Delete-Agent-" + uid);

        mvc.perform(delete("/api/immeubles/{id}", imm.id()).header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID projectId = createProject();
        String nom = "Résidence Atlas " + uid;

        ImmeubleResponse imm = doCreate(adminBearer, projectId, nom);

        assertThat(imm.id()).isNotNull();
        assertThat(imm.nom()).isEqualTo(nom);
        assertThat(imm.projectId()).isEqualTo(projectId);
    }

    @Test
    void create_asManager_returns201() throws Exception {
        UUID projectId = createProject();

        ImmeubleResponse imm = doCreate(managerBearer, projectId, "Manager Block " + uid);
        assertThat(imm.id()).isNotNull();
    }

    @Test
    void getById_returnsCorrectImmeuble() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse created = doCreate(adminBearer, projectId, "Get Test " + uid);

        mvc.perform(get("/api/immeubles/{id}", created.id()).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.nom").value(created.nom()));
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/immeubles/{id}", UUID.randomUUID()).header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_withoutProjectFilter_returnsAllBuildingsForSociete() throws Exception {
        UUID p1 = createProject();
        UUID p2 = createProject();
        doCreate(adminBearer, p1, "Block P1-" + uid);
        doCreate(adminBearer, p2, "Block P2-" + uid);

        mvc.perform(get("/api/immeubles").header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nom == 'Block P1-" + uid + "')]").exists())
                .andExpect(jsonPath("$[?(@.nom == 'Block P2-" + uid + "')]").exists());
    }

    @Test
    void list_withProjectFilter_returnsOnlyMatchingBuildings() throws Exception {
        UUID p1 = createProject();
        UUID p2 = createProject();
        doCreate(adminBearer, p1, "Filter-P1-" + uid);
        doCreate(adminBearer, p2, "Filter-P2-" + uid);

        mvc.perform(get("/api/immeubles").param("projectId", p1.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nom == 'Filter-P1-" + uid + "')]").exists())
                .andExpect(jsonPath("$[?(@.nom == 'Filter-P2-" + uid + "')]").doesNotExist());
    }

    @Test
    void update_changesNom() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse created = doCreate(adminBearer, projectId, "Original " + uid);
        String newNom = "Updated " + uid;

        mvc.perform(put("/api/immeubles/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateImmeubleRequest(newNom, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value(newNom));
    }

    @Test
    void update_asManager_returns200() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse created = doCreate(adminBearer, projectId, "MgrUpdate " + uid);

        mvc.perform(put("/api/immeubles/{id}", created.id())
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateImmeubleRequest("MgrUpdated " + uid, null, null, null))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_asAdmin_returns204_andGetReturns404() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse created = doCreate(adminBearer, projectId, "ToDelete " + uid);

        mvc.perform(delete("/api/immeubles/{id}", created.id()).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/immeubles/{id}", created.id()).header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void create_missingNom_returns400() throws Exception {
        UUID projectId = createProject();
        mvc.perform(post("/api/immeubles")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateImmeubleRequest(projectId, "", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingProjectId_returns400() throws Exception {
        mvc.perform(post("/api/immeubles")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Block A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateNomInSameProject_returns409() throws Exception {
        UUID projectId = createProject();
        String nom = "Duplex-" + uid;
        doCreate(adminBearer, projectId, nom);

        mvc.perform(post("/api/immeubles")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateImmeubleRequest(projectId, nom, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_sameNomDifferentProject_returns201() throws Exception {
        String nom = "SharedName-" + uid;
        UUID p1 = createProject();
        UUID p2 = createProject();

        doCreate(adminBearer, p1, nom);

        // Same name is allowed in a different project
        mvc.perform(post("/api/immeubles")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateImmeubleRequest(p2, nom, null, null, null))))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // Cross-société isolation
    // =========================================================================

    @Test
    void getById_crossSociete_returns404() throws Exception {
        UUID projectId = createProject();
        ImmeubleResponse immA = doCreate(adminBearer, projectId, "Iso-" + uid);

        // Create société B + user
        Societe societeB = societeRepository.save(new Societe("Isolation Corp", "MA"));
        User userB = userRepository.save(new User("admin-iso-imm-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        // Société B cannot see société A's building
        mvc.perform(get("/api/immeubles/{id}", immA.id()).header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_crossSociete_doesNotLeakBuildings() throws Exception {
        UUID projectId = createProject();
        doCreate(adminBearer, projectId, "Leak-check-" + uid);

        // Create société B (empty)
        Societe societeB = societeRepository.save(new Societe("Empty Corp B", "MA"));
        User userB = userRepository.save(new User("admin-leak-imm-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/immeubles").header("Authorization", bearerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ImmeubleResponse doCreate(String bearer, UUID projectId, String nom) throws Exception {
        String body = mvc.perform(post("/api/immeubles")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateImmeubleRequest(projectId, nom, null, 5, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ImmeubleResponse.class);
    }

    private UUID createProject() throws Exception {
        String ref = "IMM-PROJ-" + uid + "-" + (++counter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}
