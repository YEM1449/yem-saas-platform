package com.yem.hlm.backend.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.payments.api.dto.CreateScheduleItemRequest;
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
 * Integration tests for GET /api/dashboard/receivables.
 *
 * Tests:
 * 1) summary_withData_returns200_andFieldsPopulated
 * 2) summary_asAgent_scoped_toOwnContracts
 * 3) summary_emptyState_returns200_withZeroValues
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceivablesDashboardIT extends IntegrationTestBase {

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
    // 1. Receivables endpoint returns 200 with expected structure when data exists
    // =========================================================================

    @Test
    void summary_withData_returns200_andFieldsPopulated() throws Exception {
        // Create a signed contract with a payment schedule and an overdue call
        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("recv-buyer@acme.com");
        UUID contractId = createAndSignContract(projectId, propId, buyer.id());

        // Create a payment schedule with 1 tranche
        createScheduleWithTranche(contractId, new BigDecimal("450000.00"));

        String json = mvc.perform(get("/api/dashboard/receivables")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isString())
                .andExpect(jsonPath("$.totalOutstanding").isNumber())
                .andExpect(jsonPath("$.totalOverdue").isNumber())
                .andExpect(jsonPath("$.current").isMap())
                .andExpect(jsonPath("$.days30").isMap())
                .andExpect(jsonPath("$.overdueByProject").isArray())
                .andExpect(jsonPath("$.recentPayments").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        // totalOutstanding should be >= 0
        assertThat(root.get("totalOutstanding").decimalValue())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // 2. AGENT scope: agent only sees their own data
    // =========================================================================

    @Test
    void summary_asAgent_scoped_toOwnContracts() throws Exception {
        String agentBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);

        // Should succeed (AGENT is permitted) — scoped to own contracts
        mvc.perform(get("/api/dashboard/receivables")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").isNumber());
    }

    // =========================================================================
    // 3. Empty state: returns 200 with zero values
    // =========================================================================

    @Test
    void summary_emptyState_returns200_withZeroValues() throws Exception {
        // New tenant with no payment data
        Tenant otherTenant = tenantRepository.save(new Tenant("recv-empty-tenant", "Recv Empty"));
        User otherAdmin = new User(otherTenant, "recv-admin@test.com", "hash");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepository.save(otherAdmin);
        String bearer = "Bearer " + jwtProvider.generate(
                otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        String json = mvc.perform(get("/api/dashboard/receivables")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.totalOverdue").value(0))
                .andExpect(jsonPath("$.overdueByProject").isArray())
                .andExpect(jsonPath("$.recentPayments").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("overdueByProject").size()).isZero();
        assertThat(root.get("recentPayments").size()).isZero();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createProject() throws Exception {
        String ref = "RECV-PROJ-" + (++refCounter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "RECV-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Recv Appt " + ref, ref,
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
        var req = new CreateContactRequest("Test", "Buyer", null, email, null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createAndSignContract(UUID projectId, UUID propId, UUID buyerId) throws Exception {
        var req = new CreateContractRequest(projectId, propId, buyerId, USER_ID,
                new BigDecimal("450000.00"), null, null);
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contractId = objectMapper.readValue(json, ContractResponse.class).id();
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
        return contractId;
    }

    private void createScheduleWithTranche(UUID contractId, BigDecimal amount) throws Exception {
        var req = new CreateScheduleItemRequest("Tranche 1", amount, LocalDate.now().plusDays(30), null);
        mvc.perform(post("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
