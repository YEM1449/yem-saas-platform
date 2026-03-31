package com.yem.hlm.backend.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.immeuble.domain.Immeuble;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyCategory;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
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
 * Integration tests for PropertyController.
 * <p>
 * Tests cover:
 * - CRUD operations with RBAC (ADMIN, MANAGER, AGENT)
 * - Type-specific validation for all property types
 * - Tenant isolation
 * - Property filtering by type and status
 * - Dashboard summary and sales KPI endpoints
 * - New features: listedForSale, projectId/projectName, buildingName, category
 * - Error scenarios (401, 403, 404, 400, 409)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ImmeubleRepository immeubleRepository;

    private Societe societe;
    private User adminUser;
    private User managerUser;
    private User agentUser;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setupTestData() {
        societe = societeRepository.save(new Societe("Prop Test Societe", "MA"));

        project = new Project(societe.getId(), "Test Project");
        project = projectRepository.save(project);
        projectId = project.getId();

        adminUser = new User("admin@proptest.com", "hashedPass");
        adminUser = userRepository.save(adminUser);

        managerUser = new User("manager@proptest.com", "hashedPass");
        managerUser = userRepository.save(managerUser);

        agentUser = new User("agent@proptest.com", "hashedPass");
        agentUser = userRepository.save(agentUser);

        adminBearer = "Bearer " + jwtProvider.generate(adminUser.getId(), societe.getId(), UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(managerUser.getId(), societe.getId(), UserRole.ROLE_MANAGER);
        agentBearer = "Bearer " + jwtProvider.generate(agentUser.getId(), societe.getId(), UserRole.ROLE_AGENT);
    }

    // ===== RBAC Tests =====

    @Test
    void createProperty_asAdmin_returns201() throws Exception {
        var req = createValidVillaRequest("VIL-ADMIN-001");

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("VILLA"))
                .andExpect(jsonPath("$.category").value("VILLA"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.referenceCode").value("VIL-ADMIN-001"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);
        assertThat(created.title()).isEqualTo("Luxury Villa in Casablanca");
        assertThat(created.bedrooms()).isEqualTo(5);
        assertThat(created.category()).isEqualTo(PropertyCategory.VILLA);
        assertThat(created.listedForSale()).isFalse();
    }

    @Test
    void createProperty_asManager_returns201() throws Exception {
        var req = createValidVillaRequest("VIL-MGR-001");

        mvc.perform(post("/api/properties")
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceCode").value("VIL-MGR-001"));
    }

    @Test
    void createProperty_asAgent_returns403() throws Exception {
        var req = createValidVillaRequest("VIL-AGENT-001");

        mvc.perform(post("/api/properties")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProperty_asManager_returns200() throws Exception {
        var createReq = createValidVillaRequest("VIL-UPD-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(
                "Updated Villa Title", null, null,
                new BigDecimal("6000000.00"), PropertyStatus.ACTIVE,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );

        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", managerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Villa Title"))
                .andExpect(jsonPath("$.price").value(6000000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateProperty_asAgent_returns403() throws Exception {
        var createReq = createValidVillaRequest("VIL-UPD-002");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(
                "Hacked Title", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );

        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProperty_asAdmin_returns204() throws Exception {
        var createReq = createValidVillaRequest("VIL-DEL-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        mvc.perform(delete("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProperty_asManager_returns403() throws Exception {
        var createReq = createValidVillaRequest("VIL-DEL-002");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        mvc.perform(delete("/api/properties/{id}", created.id())
                        .header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProperties_asAgent_returns200() throws Exception {
        mvc.perform(get("/api/properties")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk());
    }

    // ===== Type-Specific Validation Tests =====

    @Test
    void createVilla_missingRequiredFields_returns400() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Incomplete Villa", "VIL-BAD-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, // missing surfaceArea, landArea, bedrooms, bathrooms
                null, null, null, null, null, null, null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Surface area required for VILLA"));
    }

    @Test
    void createTerrainVierge_withForbiddenFields_returns400() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.TERRAIN_VIERGE, "Undeveloped Land", "TER-BAD-001", new BigDecimal("800000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("2000"), // surfaceAreaSqm - FORBIDDEN
                new BigDecimal("2000"), // landAreaSqm - OK
                3, // bedrooms - FORBIDDEN
                null, null, null, null, null, null, null, null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bedrooms/bathrooms/building_year/surface_area not applicable to TERRAIN_VIERGE"));
    }

    @Test
    void createAppartement_withAllRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Modern Apartment", "APP-001", new BigDecimal("1500000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("120"), null, 3, 2, null, null, null, null, 2023, 5,
                null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("APPARTEMENT"))
                .andExpect(jsonPath("$.category").value("APARTMENT"))
                .andExpect(jsonPath("$.floorNumber").value(5));
    }

    @Test
    void createLot_withAllRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.LOT, "Serviced Land Plot", "LOT-001", new BigDecimal("1200000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, new BigDecimal("500"), null, null, null, null, null, null, null, null,
                "RESIDENTIAL", true, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LOT"))
                .andExpect(jsonPath("$.category").value("LAND"))
                .andExpect(jsonPath("$.zoning").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.isServiced").value(true));
    }

    @Test
    void createStudio_withRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.STUDIO, "Studio Agdal", "STU-001", new BigDecimal("600000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("35"), null, null, null, null, null, null, null, null, 3,
                null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("STUDIO"))
                .andExpect(jsonPath("$.category").value("APARTMENT"));
    }

    @Test
    void createT2_withRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.T2, "T2 Agdal", "T2-001", new BigDecimal("900000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("65"), null, 2, 1, null, null, null, null, null, 2,
                null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("T2"))
                .andExpect(jsonPath("$.category").value("APARTMENT"));
    }

    @Test
    void createCommerce_withRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.COMMERCE, "Local Commercial", "COM-001", new BigDecimal("2000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("150"), null, null, null, null, null, null, null, null, 0,
                null, null, null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COMMERCE"))
                .andExpect(jsonPath("$.category").value("COMMERCE"));
    }

    @Test
    void createProperty_withListedForSaleAndProject_returnsCorrectFields() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Project Villa", "VIL-PROJ-001", new BigDecimal("5000000"), "MAD",
                new BigDecimal("5.0"), null, "123 Palm Ave", "Casablanca", "Grand Casablanca", "20000",
                null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Villa with pool", null,
                true,      // listedForSale
                projectId, null, // projectId
                "Villa A"  // buildingName
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listedForSale").value(true))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andExpect(jsonPath("$.buildingName").value("Villa A"));
    }

    @Test
    void updateProperty_setListedForSale_persists() throws Exception {
        var createReq = createValidVillaRequest("VIL-LIST-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);
        assertThat(created.listedForSale()).isFalse();

        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                true, null, null, "Bâtiment B"
        );

        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listedForSale").value(true))
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andExpect(jsonPath("$.buildingName").value("Bâtiment B"));
    }

    // ===== Filtering Tests =====

    @Test
    void listProperties_filterByType_returnsOnlyVillas() throws Exception {
        var villa = createValidVillaRequest("VIL-FILTER-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated());

        var app = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Apartment", "APP-FILTER-001", new BigDecimal("1000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("80"), null, 2, 1, null, null, null, null, 2020, 3, null, null, null, null,
                null, projectId, null, null
        );
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/properties?type=VILLA")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='VILLA')]", hasSize(1)));
    }

    @Test
    void listProperties_filterByStatus_returnsOnlyActive() throws Exception {
        var draft = createValidVillaRequest("VIL-DRAFT-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draft)))
                .andExpect(status().isCreated());

        var active = createValidVillaRequest("VIL-ACTIVE-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/properties?status=ACTIVE")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status=='ACTIVE')]", hasSize(1)));
    }

    @Test
    void listProperties_filterByTypeAndStatus_returnsCombined() throws Exception {
        var villa = createValidVillaRequest("VIL-COMBO-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        var draftVilla = createValidVillaRequest("VIL-COMBO-002");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftVilla)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/properties?type=VILLA&status=ACTIVE")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.referenceCode=='VIL-COMBO-001')]", hasSize(1)));
    }

    // ===== Tenant Isolation Tests =====

    @Test
    void listProperties_tenantIsolation_returnsOnlyOwnProperties() throws Exception {
        var prop1 = createValidVillaRequest("VIL-TENANT-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prop1)))
                .andExpect(status().isCreated());

        Societe otherTenant = societeRepository.save(new Societe("Other Societe", "MA"));

        var otherProject = new Project(otherTenant.getId(), "Other Project");
        otherProject = projectRepository.save(otherProject);
        UUID otherProjectId = otherProject.getId();

        User otherAdmin = new User("admin@other.com", "hashedPass");
        otherAdmin = userRepository.save(otherAdmin);
        String otherBearer = "Bearer " + jwtProvider.generate(otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        var prop2 = new PropertyCreateRequest(
                PropertyType.VILLA, "Luxury Villa in Casablanca", "VIL-TENANT-002",
                new BigDecimal("5000000.00"), "MAD",
                new BigDecimal("5.0"), null, "123 Palm Avenue", "Casablanca", "Grand Casablanca", "20000",
                null, null, null, null, null, null,
                new BigDecimal("350.00"), new BigDecimal("800.00"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Villa with beautiful garden and pool", null,
                null, otherProjectId, null, null
        );
        mvc.perform(post("/api/properties")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prop2)))
                .andExpect(status().isCreated());

        String json = mvc.perform(get("/api/properties")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse[] properties = objectMapper.readValue(json, PropertyResponse[].class);
        assertThat(properties).hasSize(1);
        assertThat(properties[0].referenceCode()).isEqualTo("VIL-TENANT-001");
    }

    // ===== Error Scenario Tests =====

    @Test
    void createProperty_withoutToken_returns401() throws Exception {
        var req = createValidVillaRequest("VIL-NOAUTH-001");

        mvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProperty_notFound_returns404() throws Exception {
        mvc.perform(get("/api/properties/{id}", UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProperty_duplicateReferenceCode_returns409() throws Exception {
        var req = createValidVillaRequest("VIL-DUP-001");

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void createProperty_invalidData_returns400() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "", "VIL-INVALID-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, null, null, null, null, null, null, null, null,
                null, null,
                null, projectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProperty_withoutProjectId_returns400() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Villa Sans Projet", "VIL-NOPROJ-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Villa without project", null,
                null, null, null, null  // projectId is null — @NotNull should reject
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProperty_withNonExistentProjectId_returns404() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Villa Bad Project", "VIL-BADPROJ-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Villa with bad project ref", null,
                null, UUID.randomUUID(), null, null  // projectId doesn't exist
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProperty_withNonExistentImmeubleId_returns404() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Villa Bad Immeuble", "VIL-BAD-IMM-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Villa with bad immeuble ref", null,
                null, projectId, UUID.randomUUID(), null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", startsWith("Immeuble not found:")));
    }

    // ===== Archived Project Tests =====

    @Test
    void createProperty_withArchivedProject_returns400() throws Exception {
        var archived = new Project(societe.getId(), "Archived Project");
        archived.setStatus(ProjectStatus.ARCHIVED);
        archived = projectRepository.save(archived);
        final UUID archivedProjectId = archived.getId();

        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Villa in Archived Project", "VIL-ARCH-001", new BigDecimal("5000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, 2, 3, true, true, 2020, null, null, null,
                "Should be rejected", null,
                null, archivedProjectId, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PROJECT"));
    }

    @Test
    void updateProperty_reassignToArchivedProject_returns400() throws Exception {
        var createReq = createValidVillaRequest("VIL-ARCH-002");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID villaId = objectMapper.readValue(json, PropertyResponse.class).id();

        var archived = new Project(societe.getId(), "Archived Target");
        archived.setStatus(ProjectStatus.ARCHIVED);
        archived = projectRepository.save(archived);
        final UUID archivedProjectId = archived.getId();

        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, archivedProjectId, null, null
        );

        mvc.perform(put("/api/properties/{id}", villaId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PROJECT"));
    }

    @Test
    void updateProperty_withNonExistentImmeubleId_returns404() throws Exception {
        var createReq = createValidVillaRequest("VIL-UPD-IMM-404");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID propertyId = objectMapper.readValue(json, PropertyResponse.class).id();

        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, UUID.randomUUID(), null
        );

        mvc.perform(put("/api/properties/{id}", propertyId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", startsWith("Immeuble not found:")));
    }

    @Test
    void updateProperty_reassignProjectWithoutImmeuble_clearsExistingImmeuble() throws Exception {
        Immeuble immeuble = createImmeuble(project, "Tour A");

        var createReq = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Building Unit", "APP-IMM-001", new BigDecimal("1500000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("120"), null, 3, 2, null, null, null, null, 2023, 5,
                null, null, null, null,
                null, projectId, immeuble.getId(), null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID propertyId = objectMapper.readValue(json, PropertyResponse.class).id();

        Project newProject = projectRepository.save(new Project(societe.getId(), "Second Project"));
        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, newProject.getId(), null, null
        );

        String updatedJson = mvc.perform(put("/api/properties/{id}", propertyId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(newProject.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        PropertyResponse updated = objectMapper.readValue(updatedJson, PropertyResponse.class);
        assertThat(updated.immeubleId()).isNull();
        assertThat(updated.immeubleName()).isNull();
    }

    @Test
    void updateProperty_reassignProjectWithMismatchedImmeuble_returns400() throws Exception {
        Immeuble immeuble = createImmeuble(project, "Tour A");

        var createReq = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Building Unit", "APP-IMM-002", new BigDecimal("1500000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("120"), null, 3, 2, null, null, null, null, 2023, 5,
                null, null, null, null,
                null, projectId, immeuble.getId(), null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID propertyId = objectMapper.readValue(json, PropertyResponse.class).id();

        Project newProject = projectRepository.save(new Project(societe.getId(), "Third Project"));
        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, newProject.getId(), immeuble.getId(), null
        );

        mvc.perform(put("/api/properties/{id}", propertyId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Immeuble " + immeuble.getId() + " does not belong to project " + newProject.getId()));
    }

    @Test
    void createProperty_withActiveProject_returns201() throws Exception {
        // The default projectId in setUp is ACTIVE — this is already covered elsewhere,
        // but explicitly verify the happy path here for clarity.
        var req = createValidVillaRequest("VIL-ACTIVE-PROJ-001");

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));
    }

    // ===== Dashboard Tests =====

    @Test
    void getDashboardSummary_asAdmin_returns200() throws Exception {
        var villa = createValidVillaRequest("VIL-DASH-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated());

        mvc.perform(get("/dashboard/properties/summary?preset=LAST_30_DAYS")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDraft").isNumber())
                .andExpect(jsonPath("$.countByType").exists())
                .andExpect(jsonPath("$.avgPriceByType").exists());
    }

    @Test
    void getDashboardSummary_asAgent_returns403() throws Exception {
        mvc.perform(get("/dashboard/properties/summary?preset=LAST_30_DAYS")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboardSummary_invalidPeriod_returns400() throws Exception {
        mvc.perform(get("/dashboard/properties/summary?from=2024-12-31&to=2024-01-01")
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSalesKpi_asAdmin_returns200WithLists() throws Exception {
        mvc.perform(get("/dashboard/properties/sales-kpi?preset=LAST_30_DAYS")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").exists())
                .andExpect(jsonPath("$.to").exists())
                .andExpect(jsonPath("$.salesByProjectAgent").isArray())
                .andExpect(jsonPath("$.salesByBuilding").isArray());
    }

    @Test
    void getSalesKpi_asAgent_returns403() throws Exception {
        mvc.perform(get("/dashboard/properties/sales-kpi?preset=LAST_30_DAYS")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // ===== Update Validation Tests =====

    @Test
    void updateLot_withForbiddenBedrooms_returns400() throws Exception {
        var lotReq = new PropertyCreateRequest(
                PropertyType.LOT, "Test Lot", "LOT-UPD-001", new BigDecimal("300000"), "MAD",
                null, null, null, "Rabat", null, null, null, null, null, null, null, null,
                null, new BigDecimal("500"), null, null, null, null, null, null, null, null,
                "residential", true, null, null,
                null, projectId, null, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lotId = objectMapper.readValue(json, PropertyResponse.class).id();

        var updateReq = new PropertyUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                null, null, 3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", lotId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTerrainVierge_withForbiddenSurface_returns400() throws Exception {
        var terrainReq = new PropertyCreateRequest(
                PropertyType.TERRAIN_VIERGE, "Raw Land", "TER-UPD-001", new BigDecimal("200000"), "MAD",
                null, null, null, "Kenitra", null, null, null, null, null, null, null, null,
                null, new BigDecimal("1000"), null, null, null, null, null, null, null, null,
                null, null, null, null,
                null, projectId, null, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(terrainReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID terrainId = objectMapper.readValue(json, PropertyResponse.class).id();

        var updateReq = new PropertyUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("150"), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", terrainId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateVilla_withAllowedSurface_returns200() throws Exception {
        var villaReq = createValidVillaRequest("VIL-UPD-SURF-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villaReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID villaId = objectMapper.readValue(json, PropertyResponse.class).id();

        var updateReq = new PropertyUpdateRequest(null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("500"), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", villaId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaceAreaSqm").value(500));
    }

    // ===== Helper Methods =====

    private PropertyCreateRequest createValidVillaRequest(String referenceCode) {
        return new PropertyCreateRequest(
                PropertyType.VILLA,
                "Luxury Villa in Casablanca",
                referenceCode,
                new BigDecimal("5000000.00"),
                "MAD",
                new BigDecimal("5.0"), // commissionRate
                null, // estimatedValue
                "123 Palm Avenue", // address
                "Casablanca", // city
                "Grand Casablanca", // region
                "20000", // postalCode
                null, // latitude
                null, // longitude
                null, // titleDeedNumber
                null, // cadastralReference
                null, // ownerName
                null, // legalStatus
                new BigDecimal("350.00"), // surfaceAreaSqm
                new BigDecimal("800.00"), // landAreaSqm
                5, // bedrooms
                4, // bathrooms
                2, // floors
                3, // parkingSpaces
                true, // hasGarden
                true, // hasPool
                2020, // buildingYear
                null, // floorNumber
                null, // zoning
                null, // isServiced
                "Villa with beautiful garden and pool", // description
                null,      // notes
                null,      // listedForSale
                projectId, null, // projectId
                null       // buildingName
        );
    }

    private Immeuble createImmeuble(Project targetProject, String nom) {
        return immeubleRepository.save(new Immeuble(societe.getId(), targetProject, nom));
    }
}
