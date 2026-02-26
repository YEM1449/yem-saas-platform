package com.yem.hlm.backend.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.project.api.dto.ProjectUpdateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ProjectController.
 * <p>
 * Tests cover:
 * - CRUD with RBAC (ADMIN, MANAGER, AGENT)
 * - Duplicate name constraint (409)
 * - activeOnly filtering
 * - Tenant isolation
 * - KPI endpoint (empty project, project with properties)
 * - Error scenarios (401, 403, 404, 400, 409)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private Tenant tenant;
    private User adminUser;
    private User managerUser;
    private User agentUser;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;

    private UUID projectId;

    @BeforeEach
    void setupTestData() throws Exception {
        String uniqueKey = "proj-test-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = new Tenant(uniqueKey, "Project Test Tenant");
        tenant = tenantRepository.save(tenant);

        adminUser = new User(tenant, "admin@projtest.com", "hashedPass");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser = userRepository.save(adminUser);

        managerUser = new User(tenant, "manager@projtest.com", "hashedPass");
        managerUser.setRole(UserRole.ROLE_MANAGER);
        managerUser = userRepository.save(managerUser);

        agentUser = new User(tenant, "agent@projtest.com", "hashedPass");
        agentUser.setRole(UserRole.ROLE_AGENT);
        agentUser = userRepository.save(agentUser);

        adminBearer = "Bearer " + jwtProvider.generate(adminUser.getId(), tenant.getId(), UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(managerUser.getId(), tenant.getId(), UserRole.ROLE_MANAGER);
        agentBearer = "Bearer " + jwtProvider.generate(agentUser.getId(), tenant.getId(), UserRole.ROLE_AGENT);

        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Project\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        projectId = UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    // ===== RBAC: Create =====

    @Test
    void createProject_asAdmin_returns201() throws Exception {
        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Project\",\"description\":\"A new project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Admin Project"))
                .andExpect(jsonPath("$.description").value("A new project"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        var created = objectMapper.readTree(json);
        assertThat(UUID.fromString(created.get("tenantId").asText())).isEqualTo(tenant.getId());
    }

    @Test
    void createProject_asManager_returns201() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manager Project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Manager Project"));
    }

    @Test
    void createProject_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agent Project\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProject_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Auth\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProject_blankName_returns400() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProject_duplicateName_returns409() throws Exception {
        // "Test Project" was already created in @BeforeEach
        mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Project\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NAME_EXISTS"));
    }

    // ===== Read =====

    @Test
    void getProject_byId_returns200() throws Exception {
        mvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getProject_asAgent_returns200() throws Exception {
        mvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk());
    }

    @Test
    void getProject_notFound_returns404() throws Exception {
        mvc.perform(get("/api/projects/{id}", UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProjects_asAdmin_includesDefaultProject() throws Exception {
        mvc.perform(get("/api/projects")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.name=='Test Project')]", hasSize(1)));
    }

    @Test
    void listProjects_asAgent_returns200() throws Exception {
        mvc.perform(get("/api/projects")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk());
    }

    @Test
    void listProjects_activeOnly_excludesArchived() throws Exception {
        // Create a second project that stays ACTIVE
        mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Active Project\"}"))
                .andExpect(status().isCreated());

        // Archive the default "Test Project"
        mvc.perform(delete("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects?activeOnly=true")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Test Project')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.name=='Active Project')]", hasSize(1)));
    }

    // ===== Tenant Isolation =====

    @Test
    void getProject_crossTenant_returns404() throws Exception {
        String otherKey = "proj-iso-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant otherTenant = tenantRepository.save(new Tenant(otherKey, "Other Tenant"));
        User otherAdmin = new User(otherTenant, "admin@proj-iso.com", "hashedPass");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepository.save(otherAdmin);
        String otherBearer = "Bearer " + jwtProvider.generate(otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", otherBearer))
                .andExpect(status().isNotFound());
    }

    // ===== Update =====

    @Test
    void updateProject_asAdmin_returns200() throws Exception {
        var req = new ProjectUpdateRequest("Renamed Project", "New description", null);

        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Project"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateProject_asManager_returns200() throws Exception {
        var req = new ProjectUpdateRequest("Manager Rename", null, null);

        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Manager Rename"));
    }

    @Test
    void updateProject_asAgent_returns403() throws Exception {
        var req = new ProjectUpdateRequest("Hacked Name", null, null);

        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProject_notFound_returns404() throws Exception {
        var req = new ProjectUpdateRequest("Whatever", null, null);

        mvc.perform(put("/api/projects/{id}", UUID.randomUUID())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProject_duplicateName_returns409() throws Exception {
        mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second Project\"}"))
                .andExpect(status().isCreated());

        var req = new ProjectUpdateRequest("Second Project", null, null);
        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NAME_EXISTS"));
    }

    @Test
    void updateProject_blankName_returns400() throws Exception {
        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateProject_nullName_keepsExistingName() throws Exception {
        // Partial update: null name must not overwrite the existing name
        mvc.perform(put("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.description").value("Updated desc"));
    }

    // ===== Archive =====

    @Test
    void archiveProject_asAdmin_returns204_thenGetShowsArchived() throws Exception {
        mvc.perform(delete("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void archiveProject_asManager_returns403() throws Exception {
        mvc.perform(delete("/api/projects/{id}", projectId)
                        .header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveProject_notFound_returns404() throws Exception {
        mvc.perform(delete("/api/projects/{id}", UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // ===== KPI Endpoint =====

    @Test
    void getKpis_emptyProject_returnsZeroes() throws Exception {
        mvc.perform(get("/api/projects/{id}/kpis", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andExpect(jsonPath("$.totalProperties").value(0))
                .andExpect(jsonPath("$.depositsCount").value(0))
                .andExpect(jsonPath("$.salesCount").value(0));
    }

    @Test
    void getKpis_withOneProperty_returnsTotalOne() throws Exception {
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "KPI Villa", "VIL-KPI-001",
                new BigDecimal("5000000"), "MAD",
                null, null, "123 Palm Ave", "Casablanca", null, null,
                null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "KPI Villa desc", null,
                null, projectId, null
        );
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/projects/{id}/kpis", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProperties").value(1))
                .andExpect(jsonPath("$.propertiesByType.VILLA").value(1))
                .andExpect(jsonPath("$.statusBreakdown.DRAFT").value(1))
                .andExpect(jsonPath("$.depositsCount").value(0))
                .andExpect(jsonPath("$.salesCount").value(0));
    }

    @Test
    void getKpis_asManager_returns200() throws Exception {
        mvc.perform(get("/api/projects/{id}/kpis", projectId)
                        .header("Authorization", managerBearer))
                .andExpect(status().isOk());
    }

    @Test
    void getKpis_asAgent_returns403() throws Exception {
        mvc.perform(get("/api/projects/{id}/kpis", projectId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void getKpis_notFound_returns404() throws Exception {
        mvc.perform(get("/api/projects/{id}/kpis", UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }
}
