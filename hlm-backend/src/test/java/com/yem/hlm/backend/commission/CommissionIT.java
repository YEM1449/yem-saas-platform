package com.yem.hlm.backend.commission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for commission endpoints.
 *
 * Tests:
 * 1) calculation_withTenantDefaultRule_returnsCorrectCommission
 * 2) projectSpecificRule_takesPriorityOver_tenantDefault
 * 3) agent_canOnly_seeOwnCommissions (GET /api/commissions/my)
 * 4) admin_canSeeAllCommissions (GET /api/commissions)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommissionIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. Tenant-wide default rule: commissionAmount = agreedPrice * rate / 100
    // =========================================================================

    @Test
    void calculation_withTenantDefaultRule_returnsCorrectCommission() throws Exception {
        // Create tenant-wide rule: 2% rate, no project scope
        createRule(null, new BigDecimal("2.00"), null,
                LocalDate.now().minusDays(30).toString(), null);

        UUID projectId  = createProject();
        UUID propId     = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("comm-buyer1@acme.com");
        UUID contractId = createAndSignContract(projectId, propId, buyer.id(),
                new BigDecimal("500000.00"), null);

        // GET /api/commissions?agentId=USER_ID
        String json = mvc.perform(get("/api/commissions")
                        .param("agentId", USER_ID.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(json);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);

        // Find the contract we just created
        JsonNode entry = null;
        for (JsonNode n : arr) {
            if (contractId.toString().equals(n.get("contractId").asText())) {
                entry = n; break;
            }
        }
        assertThat(entry).isNotNull();
        assertThat(entry.get("ratePercent").decimalValue()).isEqualByComparingTo("2.00");
        // 500000 * 2 / 100 = 10000
        assertThat(entry.get("commissionAmount").decimalValue()).isEqualByComparingTo("10000.00");
    }

    // =========================================================================
    // 2. Project-specific rule takes priority over tenant default
    // =========================================================================

    @Test
    void projectSpecificRule_takesPriorityOver_tenantDefault() throws Exception {
        UUID projectId = createProject();

        // Tenant default: 2%
        createRule(null, new BigDecimal("2.00"), null,
                LocalDate.now().minusDays(30).toString(), null);
        // Project-specific: 5%
        createRule(projectId.toString(), new BigDecimal("5.00"), null,
                LocalDate.now().minusDays(30).toString(), null);

        UUID propId     = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("comm-buyer2@acme.com");
        UUID contractId = createAndSignContract(projectId, propId, buyer.id(),
                new BigDecimal("200000.00"), null);

        String json = mvc.perform(get("/api/commissions")
                        .param("agentId", USER_ID.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(json);
        JsonNode entry = null;
        for (JsonNode n : arr) {
            if (contractId.toString().equals(n.get("contractId").asText())) {
                entry = n; break;
            }
        }
        assertThat(entry).isNotNull();
        // Project-specific 5% should win: 200000 * 5 / 100 = 10000
        assertThat(entry.get("ratePercent").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(entry.get("commissionAmount").decimalValue()).isEqualByComparingTo("10000.00");
    }

    // =========================================================================
    // 3. AGENT sees only own commissions via /api/commissions/my
    // =========================================================================

    @Test
    void agent_canOnly_seeOwnCommissions() throws Exception {
        createRule(null, new BigDecimal("2.00"), null,
                LocalDate.now().minusDays(30).toString(), null);

        UUID projectId = createProject();

        // Create a second agent in same tenant
        Tenant tenant = tenantRepository.findById(TENANT_ID).orElseThrow();
        User agentB = new User(tenant, "comm-agent-b@acme.com", "hash");
        agentB.setRole(UserRole.ROLE_AGENT);
        agentB = userRepository.save(agentB);

        // Sign 1 contract with seed admin (USER_ID) and 1 with agentB
        UUID propA = createAndActivateProperty(projectId);
        UUID propB = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("comm-buyer3@acme.com");

        createAndSignContract(projectId, propA, buyer.id(), new BigDecimal("100000"), null);

        String agentBAdminBearer = "Bearer " +
                jwtProvider.generate(agentB.getId(), TENANT_ID, UserRole.ROLE_ADMIN);
        createAndSignContract(projectId, propB, buyer.id(),
                new BigDecimal("200000"), agentBAdminBearer);

        // AGENT calls /my — should see only own contract (1 result for USER_ID)
        String agentBearer = "Bearer " +
                jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);
        String json = mvc.perform(get("/api/commissions/my")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(json);
        // All returned entries should belong to USER_ID
        for (JsonNode n : arr) {
            assertThat(n.get("agentId").asText()).isEqualTo(USER_ID.toString());
        }
    }

    // =========================================================================
    // 4. ADMIN sees all commissions (no agentId filter)
    // =========================================================================

    @Test
    void admin_canSeeAllCommissions() throws Exception {
        createRule(null, new BigDecimal("3.00"), null,
                LocalDate.now().minusDays(30).toString(), null);

        UUID projectId = createProject();
        Tenant tenant  = tenantRepository.findById(TENANT_ID).orElseThrow();
        User agentC = new User(tenant, "comm-agent-c@acme.com", "hash");
        agentC.setRole(UserRole.ROLE_AGENT);
        agentC = userRepository.save(agentC);

        UUID propA = createAndActivateProperty(projectId);
        UUID propB = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("comm-buyer4@acme.com");

        createAndSignContract(projectId, propA, buyer.id(), new BigDecimal("150000"), null);

        String agentCBearer = "Bearer " +
                jwtProvider.generate(agentC.getId(), TENANT_ID, UserRole.ROLE_ADMIN);
        createAndSignContract(projectId, propB, buyer.id(),
                new BigDecimal("250000"), agentCBearer);

        // ADMIN (no agentId filter) — should see both contracts
        String json = mvc.perform(get("/api/commissions")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(json);
        assertThat(arr.size()).isGreaterThanOrEqualTo(2);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void createRule(String projectId, BigDecimal rate, BigDecimal fixed,
                             String from, String to) throws Exception {
        String body = "{\"ratePercent\":" + rate +
                (projectId != null ? ",\"projectId\":\"" + projectId + "\"" : "") +
                (fixed     != null ? ",\"fixedAmount\":" + fixed : "") +
                ",\"effectiveFrom\":\"" + from + "\"" +
                (to != null ? ",\"effectiveTo\":\"" + to + "\"" : "") +
                "}";
        mvc.perform(post("/api/commission-rules")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private UUID createProject() throws Exception {
        String ref = "COMM-PROJ-" + (++refCounter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "COMM-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Comm Appt " + ref, ref,
                new BigDecimal("450000"), "MAD",
                null, null, null, "Rabat", null, null, null, null,
                null, null, null, null,
                new BigDecimal("90"), null,
                2, 1, 0, null, null, null, null, 1, null, null, null, null,
                null, projectId, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse prop = objectMapper.readValue(json, PropertyResponse.class);
        mvc.perform(put("/api/properties/{id}", prop.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PropertyUpdateRequest(
                                null, null, null, null, PropertyStatus.ACTIVE,
                                null, null, null, null, null, null, null, null,
                                null, null, null, null, null, null, null, null, null,
                                null, null, null))))
                .andExpect(status().isOk());
        return prop.id();
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Test", "Buyer", null, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createAndSignContract(UUID projectId, UUID propId, UUID buyerId,
                                       BigDecimal price, String bearer) throws Exception {
        String b = bearer != null ? bearer : adminBearer;
        var req = new CreateContractRequest(projectId, propId, buyerId,
                USER_ID, price, null, null);
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contractId = objectMapper.readValue(json, ContractResponse.class).id();
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", b))
                .andExpect(status().isOk());
        return contractId;
    }
}
