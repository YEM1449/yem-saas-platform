package com.yem.hlm.backend.viewer3d;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.viewer3d.api.dto.*;
import com.yem.hlm.backend.viewer3d.repo.Lot3dMappingRepository;
import com.yem.hlm.backend.viewer3d.repo.Project3dModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Project3dController.
 *
 * <p>Uses seed data: SOCIETE_ID (acme) and ADMIN_USER_ID (admin@acme.com).
 * No {@code @Transactional} — AuditEventListener uses REQUIRES_NEW.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Auth guards (401/403 per role)</li>
 *   <li>Happy path: upsert model, get model, bulk mappings, status snapshot</li>
 *   <li>Upload URL generation (step 1)</li>
 *   <li>Cross-société isolation (project from another société → 4xx)</li>
 *   <li>Missing model → 404 (RG-10)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class Project3dControllerIT extends IntegrationTestBase {

    private static final UUID SOCIETE_ID    = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // A project seeded in changeset 008 (acme's first project)
    // We create one dynamically in @BeforeEach if no seed project is available
    private static final String PROJECTS_BASE = "/api/projects";

    @Autowired MockMvc                  mvc;
    @Autowired ObjectMapper             mapper;
    @Autowired JwtProvider              jwtProvider;
    @Autowired Project3dModelRepository modelRepo;
    @Autowired Lot3dMappingRepository   mappingRepo;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;
    private UUID   projectId;

    @BeforeEach
    void setUp() throws Exception {
        adminBearer   = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_MANAGER);
        agentBearer   = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_AGENT);

        // Create a fresh project via the API so we have a known projectId for this run
        String uid  = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"name":"3D Test Project %s","description":"IT test","adresse":"1 rue Test",
                 "ville":"Paris","codePostal":"75001"}""".formatted(uid);

        String result = mvc.perform(post(PROJECTS_BASE)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        projectId = UUID.fromString(mapper.readTree(result).get("id").asText());

        // Clean up any 3D data from previous test runs for this project
        modelRepo.findBySocieteIdAndProjetId(SOCIETE_ID, projectId)
                .ifPresent(modelRepo::delete);
        mappingRepo.deleteAllBySocieteIdAndProjetId(SOCIETE_ID, projectId);
    }

    // ── Auth guards ───────────────────────────────────────────────────────────

    @Test
    void upsertModel_withoutToken_returns401() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"glbFileKey":"models/s/p/file.glb","dracoCompressed":true}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsertModel_withAgentRole_returns403() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"glbFileKey":"models/s/p/file.glb","dracoCompressed":true}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkMappings_withManagerRole_returns403() throws Exception {
        mvc.perform(put(PROJECTS_BASE + "/{id}/3d-model/mappings", projectId)
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mappings":[]}"""))
                .andExpect(status().isForbidden());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void upsertModel_asAdmin_returns201_withPresignedUrl() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"glbFileKey":"models/s/p/file.glb","dracoCompressed":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projetId").value(projectId.toString()))
                .andExpect(jsonPath("$.glbPresignedUrl").exists())
                .andExpect(jsonPath("$.dracoCompressed").value(true));
    }

    @Test
    void getModel_afterUpsert_returns200() throws Exception {
        // Upsert first
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"glbFileKey":"models/s/p/test.glb","dracoCompressed":true}"""))
                .andExpect(status().isCreated());

        // Then get
        mvc.perform(get(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projetId").value(projectId.toString()))
                .andExpect(jsonPath("$.mappings").isArray());
    }

    @Test
    void getModel_whenNoneExists_returns404() throws Exception {
        mvc.perform(get(PROJECTS_BASE + "/{id}/3d-model", projectId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatusSnapshot_returns200_emptyList_whenNoMappings() throws Exception {
        mvc.perform(get(PROJECTS_BASE + "/{id}/3d-properties-status", projectId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void bulkMappings_asAdmin_returns204() throws Exception {
        mvc.perform(put(PROJECTS_BASE + "/{id}/3d-model/mappings", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mappings":[]}"""))
                .andExpect(status().isNoContent());
    }

    // ── Upload URL (step 1) ───────────────────────────────────────────────────

    @Test
    void uploadUrl_asManager_returns200_withFileKey() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model/upload-url", projectId)
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"building.glb","fileSizeBytes":5000000,"dracoCompressed":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileKey").value(
                        org.hamcrest.Matchers.startsWith("models/" + SOCIETE_ID + "/" + projectId)))
                .andExpect(jsonPath("$.uploadUrl").exists());
    }

    @Test
    void uploadUrl_withDracoFalse_returns400() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model/upload-url", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"building.glb","fileSizeBytes":5000000,"dracoCompressed":false}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadUrl_withAgentRole_returns403() throws Exception {
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model/upload-url", projectId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"building.glb","fileSizeBytes":5000000,"dracoCompressed":true}"""))
                .andExpect(status().isForbidden());
    }

    // ── Cross-société isolation ───────────────────────────────────────────────

    @Test
    void upsertModel_withProjectFromOtherSociete_returns4xx() throws Exception {
        UUID otherProjectId = UUID.randomUUID(); // non-existent → 4xx
        mvc.perform(post(PROJECTS_BASE + "/{id}/3d-model", otherProjectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"glbFileKey":"models/s/p/hack.glb","dracoCompressed":true}"""))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Expect 404 (project not found) not 500 — never reveal other société data
                    org.assertj.core.api.Assertions.assertThat(status).isIn(404, 403);
                });
    }
}
