package com.yem.hlm.backend.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
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
 * Integration tests for PropertyController.
 * <p>
 * Tests cover:
 * - CRUD operations with RBAC (ADMIN, MANAGER, AGENT)
 * - Type-specific validation for all 5 property types
 * - Tenant isolation
 * - Property filtering by type and status
 * - Dashboard summary endpoint
 * - Error scenarios (401, 403, 404, 400, 409)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyControllerIT extends IntegrationTestBase {

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

    @BeforeEach
    void setupTestData() {
        // Create unique tenant for this test
        String uniqueKey = "prop-test-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = new Tenant(uniqueKey, "Property Test Tenant");
        tenant = tenantRepository.save(tenant);

        // Create users with different roles
        adminUser = new User(tenant, "admin@proptest.com", "hashedPass");
        adminUser.setRole(UserRole.ROLE_ADMIN);
        adminUser = userRepository.save(adminUser);

        managerUser = new User(tenant, "manager@proptest.com", "hashedPass");
        managerUser.setRole(UserRole.ROLE_MANAGER);
        managerUser = userRepository.save(managerUser);

        agentUser = new User(tenant, "agent@proptest.com", "hashedPass");
        agentUser.setRole(UserRole.ROLE_AGENT);
        agentUser = userRepository.save(agentUser);

        // Generate JWT tokens with roles
        adminBearer = "Bearer " + jwtProvider.generate(adminUser.getId(), tenant.getId(), UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(managerUser.getId(), tenant.getId(), UserRole.ROLE_MANAGER);
        agentBearer = "Bearer " + jwtProvider.generate(agentUser.getId(), tenant.getId(), UserRole.ROLE_AGENT);
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
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.referenceCode").value("VIL-ADMIN-001"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);
        assertThat(created.title()).isEqualTo("Luxury Villa in Casablanca");
        assertThat(created.bedrooms()).isEqualTo(5);
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
        // Create property as ADMIN
        var createReq = createValidVillaRequest("VIL-UPD-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Update as MANAGER
        var updateReq = new PropertyUpdateRequest(
                "Updated Villa Title",
                null, // description
                null, // notes
                new BigDecimal("6000000.00"),
                PropertyStatus.ACTIVE,
                null, null, null, null, null
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
        // Create property as ADMIN
        var createReq = createValidVillaRequest("VIL-UPD-002");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Try to update as AGENT
        var updateReq = new PropertyUpdateRequest(
                "Hacked Title", null, null, null, null, null, null, null, null, null
        );

        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProperty_asAdmin_returns204() throws Exception {
        // Create property
        var createReq = createValidVillaRequest("VIL-DEL-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Delete as ADMIN
        mvc.perform(delete("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        // Verify it's soft-deleted (404 on GET)
        mvc.perform(get("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProperty_asManager_returns403() throws Exception {
        // Create property
        var createReq = createValidVillaRequest("VIL-DEL-002");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Try to delete as MANAGER
        mvc.perform(delete("/api/properties/{id}", created.id())
                        .header("Authorization", managerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProperties_asAgent_returns200() throws Exception {
        // AGENT can read/list properties
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
                null, // missing surfaceAreaSqm
                null, // missing landAreaSqm
                null, // missing bedrooms
                null, // missing bathrooms
                null, null, null, null, null, null, null, null, null, null
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
                new BigDecimal("2000"), // surfaceAreaSqm - FORBIDDEN for TERRAIN_VIERGE
                new BigDecimal("2000"), // landAreaSqm - OK
                3, // bedrooms - FORBIDDEN
                null, null, null, null, null, null, null, null, null, null, null
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
                new BigDecimal("120"), // surfaceAreaSqm
                null, // landAreaSqm - not needed
                3, // bedrooms
                2, // bathrooms
                null, // floors - not needed
                null, null, null, 2023, 5, // floorNumber
                null, null, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("APPARTEMENT"))
                .andExpect(jsonPath("$.floorNumber").value(5));
    }

    @Test
    void createLot_withAllRequiredFields_returns201() throws Exception {
        var req = new PropertyCreateRequest(
                PropertyType.LOT, "Serviced Land Plot", "LOT-001", new BigDecimal("1200000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, // surfaceAreaSqm - not needed
                new BigDecimal("500"), // landAreaSqm
                null, // bedrooms - forbidden
                null, // bathrooms - forbidden
                null, null, null, null, null, null,
                "RESIDENTIAL", // zoning
                true, // isServiced
                null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LOT"))
                .andExpect(jsonPath("$.zoning").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.isServiced").value(true));
    }

    // ===== Filtering Tests =====

    @Test
    void listProperties_filterByType_returnsOnlyVillas() throws Exception {
        // Create VILLA
        var villa = createValidVillaRequest("VIL-FILTER-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated());

        // Create APPARTEMENT
        var app = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Apartment", "APP-FILTER-001", new BigDecimal("1000000"), "MAD",
                null, null, null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("80"), null, 2, 1, null, null, null, null, 2020, 3, null, null, null, null
        );
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated());

        // Filter by VILLA type
        mvc.perform(get("/api/properties?type=VILLA")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='VILLA')]", hasSize(1)));
    }

    @Test
    void listProperties_filterByStatus_returnsOnlyActive() throws Exception {
        // Create DRAFT property
        var draft = createValidVillaRequest("VIL-DRAFT-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draft)))
                .andExpect(status().isCreated());

        // Create ACTIVE property
        var active = createValidVillaRequest("VIL-ACTIVE-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Update to ACTIVE
        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE, null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Filter by ACTIVE status
        mvc.perform(get("/api/properties?status=ACTIVE")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status=='ACTIVE')]", hasSize(1)));
    }

    @Test
    void listProperties_filterByTypeAndStatus_returnsCombined() throws Exception {
        // Create VILLA ACTIVE
        var villa = createValidVillaRequest("VIL-COMBO-001");
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE, null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Create VILLA DRAFT (won't match filter)
        var draftVilla = createValidVillaRequest("VIL-COMBO-002");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftVilla)))
                .andExpect(status().isCreated());

        // Filter by VILLA AND ACTIVE
        mvc.perform(get("/api/properties?type=VILLA&status=ACTIVE")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.referenceCode=='VIL-COMBO-001')]", hasSize(1)));
    }

    // ===== Tenant Isolation Tests =====

    @Test
    void listProperties_tenantIsolation_returnsOnlyOwnProperties() throws Exception {
        // Create property in current tenant
        var prop1 = createValidVillaRequest("VIL-TENANT-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prop1)))
                .andExpect(status().isCreated());

        // Create another tenant
        String otherKey = "prop-test-other-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant otherTenant = new Tenant(otherKey, "Other Tenant");
        otherTenant = tenantRepository.save(otherTenant);

        User otherAdmin = new User(otherTenant, "admin@other.com", "hashedPass");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepository.save(otherAdmin);

        String otherBearer = "Bearer " + jwtProvider.generate(otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        // Create property in other tenant
        var prop2 = createValidVillaRequest("VIL-TENANT-002");
        mvc.perform(post("/api/properties")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prop2)))
                .andExpect(status().isCreated());

        // List properties from first tenant - should only see VIL-TENANT-001
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

        // First creation succeeds
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second creation with same reference code fails
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
                new BigDecimal("350"), new BigDecimal("800"), 5, 4, null, null, null, null, null, null, null, null, null, null
        );

        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ===== Dashboard Tests =====

    @Test
    void getDashboardSummary_asAdmin_returns200() throws Exception {
        // Create some test properties
        var villa = createValidVillaRequest("VIL-DASH-001");
        mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(villa)))
                .andExpect(status().isCreated());

        // Get dashboard summary
        mvc.perform(get("/dashboard/properties/summary?preset=LAST_30_DAYS")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDraft").isNumber())
                .andExpect(jsonPath("$.countByType").exists())
                .andExpect(jsonPath("$.avgPriceByType").exists());
    }

    @Test
    void getDashboardSummary_asAgent_returns403() throws Exception {
        // AGENT cannot access dashboard
        mvc.perform(get("/dashboard/properties/summary?preset=LAST_30_DAYS")
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboardSummary_invalidPeriod_returns400() throws Exception {
        // from > to should fail
        mvc.perform(get("/dashboard/properties/summary?from=2024-12-31&to=2024-01-01")
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest());
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
                null, // floorNumber - not applicable
                null, // zoning - not applicable
                null, // isServiced - not applicable
                "Villa with beautiful garden and pool", // description
                null  // notes
        );
    }
}
