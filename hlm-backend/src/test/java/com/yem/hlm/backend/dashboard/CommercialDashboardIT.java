package com.yem.hlm.backend.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.repo.ProspectDetailRepository;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.vente.api.dto.VenteResponse;

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
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/dashboard/commercial/summary (and /sales).
 *
 * Tests:
 * 1) summary_asManager_returns200_andTotalsMatchSeededData
 * 2) summary_asAgent_forcesAgentScope
 * 3) summary_projectFilter_crossTenant_returns404
 * 4) summary_invalidDateRange_returns400
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommercialDashboardIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProspectDetailRepository prospectDetailRepository;
    @Autowired CacheManager cacheManager;

    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
        // Evict dashboard cache to prevent stale @Cacheable results bleeding between @Transactional tests
        var cache = cacheManager.getCache(CacheConfig.COMMERCIAL_DASHBOARD_CACHE);
        if (cache != null) cache.clear();
    }

    // =========================================================================
    // 1. summary_asManager_returns200_andTotalsMatchSeededData
    // Seeds 2 signed contracts + 1 confirmed deposit and verifies KPI totals.
    // =========================================================================

    @Test
    void summary_asManager_returns200_andTotalsMatchSeededData() throws Exception {
        UUID projectId   = createProject(adminBearer);
        UUID propId1     = createAndActivateProperty(projectId, adminBearer);
        UUID propId2     = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("dash-buyer@acme.com");

        // Create 2 ventes → salesCount = 2, salesTotalAmount = 900000
        createVente(propId1, buyer.id(), new BigDecimal("450000.00"), null, adminBearer);
        createVente(propId2, buyer.id(), new BigDecimal("450000.00"), null, adminBearer);

        // Confirm 1 deposit on a 3rd property (just for deposit totals — won't block since property is ACTIVE)
        UUID propId3   = createAndActivateProperty(projectId, adminBearer);
        UUID depositId = createDeposit(propId3, buyer.id());
        confirmDeposit(depositId);

        String json = mvc.perform(get("/api/dashboard/commercial/summary")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").value(2))
                .andExpect(jsonPath("$.depositsCount").value(1))
                .andExpect(jsonPath("$.salesTotalAmount").isNumber())
                .andExpect(jsonPath("$.avgSaleValue").isNumber())
                .andExpect(jsonPath("$.inventoryByStatus").isMap())
                .andExpect(jsonPath("$.salesAmountByDay").isArray())
                .andExpect(jsonPath("$.depositsAmountByDay").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        // Two ventes at 450k each → total 900k
        assertThat(root.get("salesTotalAmount").decimalValue())
                .isEqualByComparingTo("900000.00");
        // Drill-down: verify sales list endpoint also works
        mvc.perform(get("/api/dashboard/commercial/sales")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.sales").isArray());
    }

    // =========================================================================
    // 2. summary_asAgent_forcesAgentScope
    // An AGENT token only sees their own contracts — cross-agent contracts excluded.
    // =========================================================================

    @Test
    void summary_asAgent_forcesAgentScope() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propId1   = createAndActivateProperty(projectId, adminBearer);
        UUID propId2   = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("dash-agent-scope@acme.com");

        // Create second user (AGENT B) — saved in the same test transaction, visible to MockMvc calls
        User agentB = new User("agent-b@acme.com", "hash");
        agentB = userRepository.save(agentB);

        // Vente assigned to seed USER_ID (agent A) — no explicit agentId, defaults to current user
        createVente(propId1, buyer.id(), new BigDecimal("450000.00"), null, adminBearer);

        // Vente assigned to agent B — explicit agentId in request
        createVente(propId2, buyer.id(), new BigDecimal("450000.00"), agentB.getId(), adminBearer);

        // Seed USER_ID calls dashboard as AGENT → must see only their own vente (salesCount=1)
        String agentABearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);
        mvc.perform(get("/api/dashboard/commercial/summary")
                        .header("Authorization", agentABearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").value(1));
    }

    // =========================================================================
    // 3. summary_projectFilter_crossTenant_returns404
    // Supplying a projectId from another tenant must return 404.
    // =========================================================================

    @Test
    void summary_projectFilter_crossTenant_returns404() throws Exception {
        // Create a second tenant + project
        Societe otherTenant = societeRepository.save(new Societe("Acme Corp", "MA"));
        User otherAdmin = new User("other-admin-dash@test.com", "hash");
        otherAdmin = userRepository.save(otherAdmin);
        String otherBearer = "Bearer " + jwtProvider.generate(otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        UUID otherProjectId = createProject(otherBearer);

        // Use TENANT_ID token but supply otherTenant's projectId → must be 404
        mvc.perform(get("/api/dashboard/commercial/summary")
                        .param("projectId", otherProjectId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 4. summary_invalidDateRange_returns400
    // from > to must return 400 VALIDATION_ERROR.
    // =========================================================================

    @Test
    void summary_invalidDateRange_returns400() throws Exception {
        mvc.perform(get("/api/dashboard/commercial/summary")
                        .param("from", "2026-12-31T23:59:59")
                        .param("to",   "2026-01-01T00:00:00")
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 5. summary_activeProspectsCount_reflectsContactStatuses
    // Seeds 3 prospects; confirms a deposit for 1 → converts to CLIENT.
    // Verifies dashboard reports activeProspectsCount = 2.
    // =========================================================================

    @Test
    void summary_activeProspectsCount_reflectsContactStatuses() throws Exception {
        UUID projectId = createProject(adminBearer);

        // Create 3 contacts → all default to status=PROSPECT
        createContact("dash-pros1@acme.com");
        createContact("dash-pros2@acme.com");
        ContactResponse p3 = createContact("dash-pros3@acme.com");

        // Creating a deposit promotes p3 → QUALIFIED_PROSPECT (still a prospect)
        UUID propId = createAndActivateProperty(projectId, adminBearer);
        UUID depositId = createDeposit(propId, p3.id());

        // Confirming the deposit converts p3 → CLIENT (no longer an active prospect)
        confirmDeposit(depositId);

        mvc.perform(get("/api/dashboard/commercial/summary")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProspectsCount").value(2));
    }

    // =========================================================================
    // 6. summary_discountFields_presentWhenContractHasListPrice (F3.2)
    // Signs a contract with listPrice set → avgDiscountPercent + maxDiscountPercent
    // must be non-null numbers in the response.
    // =========================================================================

    @Test
    void summary_discountFields_presentWhenContractHasListPrice() throws Exception {
        UUID projectId = createProject(adminBearer);
        // Property with catalogue price 500000 — discount computed against property.price
        UUID propId    = createAndActivateProperty(projectId, adminBearer, new BigDecimal("500000.00"));
        ContactResponse buyer = createContact("dash-disc@acme.com");

        // Vente at 450000 on a 500000-priced property → discount = (500000-450000)/500000*100 = 10%
        createVente(propId, buyer.id(), new BigDecimal("450000.00"), null, adminBearer);

        String json = mvc.perform(get("/api/dashboard/commercial/summary")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgDiscountPercent").isNumber())
                .andExpect(jsonPath("$.maxDiscountPercent").isNumber())
                .andExpect(jsonPath("$.discountByAgent").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        // (500000 - 450000) / 500000 * 100 = 10.0
        assertThat(root.get("avgDiscountPercent").decimalValue())
                .isEqualByComparingTo("10.00");
    }

    // =========================================================================
    // 7. summary_prospectSourceFunnel_returnsSourceGroups (F3.4)
    // Seeds contacts with two different sources → prospectsBySource must contain
    // at least those two groups.
    // =========================================================================

    @Test
    void summary_prospectSourceFunnel_returnsSourceGroups() throws Exception {
        // Create 3 contacts and assign sources directly via ProspectDetailRepository
        ContactResponse c1 = createContact("dash-src1@acme.com");
        ContactResponse c2 = createContact("dash-src2@acme.com");
        ContactResponse c3 = createContact("dash-src3@acme.com");

        setProspectSource(c1.id(), "WEBSITE");
        setProspectSource(c2.id(), "WEBSITE");
        setProspectSource(c3.id(), "REFERRAL");

        String json = mvc.perform(get("/api/dashboard/commercial/summary")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prospectsBySource").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        com.fasterxml.jackson.databind.JsonNode sources = root.get("prospectsBySource");

        // Verify WEBSITE and REFERRAL groups are both present
        boolean hasWebsite  = false;
        boolean hasReferral = false;
        for (com.fasterxml.jackson.databind.JsonNode entry : sources) {
            String src = entry.get("source").asText();
            if ("WEBSITE".equals(src))  hasWebsite  = true;
            if ("REFERRAL".equals(src)) hasReferral = true;
        }
        assertThat(hasWebsite).as("WEBSITE source group present").isTrue();
        assertThat(hasReferral).as("REFERRAL source group present").isTrue();
    }

    // =========================================================================
    // Private helpers (mirrors ContractControllerIT pattern)
    // =========================================================================

    private UUID createProject(String bearer) throws Exception {
        String ref = "DASH-PROJ-" + (++refCounter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId, String bearer) throws Exception {
        return createAndActivateProperty(projectId, bearer, new BigDecimal("450000.00"));
    }

    private UUID createAndActivateProperty(UUID projectId, String bearer, BigDecimal price) throws Exception {
        String ref = "DASH-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Dash Test Appt " + ref, ref,
                price, "MAD",
                null, null, null, "Rabat", null, null, null, null,
                null, null, null, null,
                new BigDecimal("90"), null,
                2, 1, 0, null, null, null, null, 1, null, null, null, null,
                null, projectId, null, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        mvc.perform(patch("/api/properties/{id}/status", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return created.id();
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Test", "Buyer", null, email, null, null, null, true, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createVente(UUID propertyId, UUID contactId, BigDecimal prix,
                             UUID agentId, String bearer) throws Exception {
        String agentPart = agentId != null ? ",\"agentId\":\"" + agentId + "\"" : "";
        String body = "{\"contactId\":\"" + contactId + "\",\"propertyId\":\"" + propertyId
                + "\",\"prixVente\":" + prix.toPlainString()
                + ",\"dateCompromis\":\"2026-04-01\"" + agentPart + "}";
        String json = mvc.perform(post("/api/ventes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, VenteResponse.class).id();
    }

    private UUID createDeposit(UUID propertyId, UUID contactId) throws Exception {
        var req = new CreateDepositRequest(
                contactId, propertyId, new BigDecimal("20000.00"),
                null, null, "MAD", null, null
        );
        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, DepositResponse.class).id();
    }

    private void confirmDeposit(UUID depositId) throws Exception {
        mvc.perform(post("/api/deposits/{id}/confirm", depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
    }

    /** Sets the prospect source on the ProspectDetail for a given contact. */
    private void setProspectSource(UUID contactId, String source) {
        prospectDetailRepository.findBySocieteIdAndContactId(TENANT_ID, contactId)
                .ifPresent(pd -> {
                    pd.setSource(source);
                    prospectDetailRepository.save(pd);
                });
    }
}
