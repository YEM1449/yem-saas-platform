package com.yem.hlm.backend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/audit/commercial.
 *
 * Tests:
 * 1) auditList_afterConfirmDeposit_containsDepositConfirmedEvent
 * 2) auditList_afterSignContract_containsContractSignedEvent
 * 3) auditList_tenantIsolation_anotherTenantCannotSeeEvents
 * 4) auditList_asAgent_returns403
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommercialAuditIT extends IntegrationTestBase {

    private static final String AUDIT_ENDPOINT = "/api/audit/commercial";
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. Deposit confirmed → DEPOSIT_CONFIRMED event in audit log
    // =========================================================================

    @Test
    void auditList_afterConfirmDeposit_containsDepositConfirmedEvent() throws Exception {
        UUID propId    = createAndActivateProperty(createProject(), adminBearer);
        UUID contactId = createContact("audit-dep@acme.com").id();
        UUID depositId = createDeposit(propId, contactId);

        mvc.perform(post("/api/deposits/{id}/confirm", depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        String json = mvc.perform(get(AUDIT_ENDPOINT)
                        .param("correlationType", "DEPOSIT")
                        .param("correlationId", depositId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode events = objectMapper.readTree(json);
        assertThat(events.isArray()).isTrue();
        boolean hasConfirmed = false;
        for (JsonNode e : events) {
            if ("DEPOSIT_CONFIRMED".equals(e.get("eventType").asText())
                    && depositId.toString().equals(e.get("correlationId").asText())) {
                hasConfirmed = true;
                break;
            }
        }
        assertThat(hasConfirmed).as("Expected DEPOSIT_CONFIRMED audit event").isTrue();
    }

    // =========================================================================
    // 2. Contract signed → CONTRACT_SIGNED event in audit log
    // =========================================================================

    @Test
    void auditList_afterSignContract_containsContractSignedEvent() throws Exception {
        UUID projId    = createProject();
        UUID propId    = createAndActivateProperty(projId, adminBearer);
        UUID contactId = createContact("audit-ctr@acme.com").id();
        UUID contractId = createDraftContract(projId, propId, contactId);

        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        String json = mvc.perform(get(AUDIT_ENDPOINT)
                        .param("correlationType", "CONTRACT")
                        .param("correlationId", contractId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode events = objectMapper.readTree(json);
        assertThat(events.isArray()).isTrue();
        boolean hasSigned = false;
        for (JsonNode e : events) {
            if ("CONTRACT_SIGNED".equals(e.get("eventType").asText())
                    && contractId.toString().equals(e.get("correlationId").asText())) {
                hasSigned = true;
                break;
            }
        }
        assertThat(hasSigned).as("Expected CONTRACT_SIGNED audit event").isTrue();
    }

    // =========================================================================
    // 3. Tenant isolation — another tenant's events are not visible
    // =========================================================================

    @Test
    void auditList_tenantIsolation_anotherTenantCannotSeeEvents() throws Exception {
        // Create tenant A deposit → audit event
        UUID propId    = createAndActivateProperty(createProject(), adminBearer);
        UUID contactId = createContact("audit-iso@acme.com").id();
        UUID depositId = createDeposit(propId, contactId);

        // Verify DEPOSIT_CREATED is visible to tenant A
        String jsonA = mvc.perform(get(AUDIT_ENDPOINT)
                        .param("correlationType", "DEPOSIT")
                        .param("correlationId", depositId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(jsonA).size()).isGreaterThanOrEqualTo(1);

        // Tenant B cannot see tenant A events
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@audit-iso-b.com", "hash");
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        String jsonB = mvc.perform(get(AUDIT_ENDPOINT)
                        .param("correlationType", "DEPOSIT")
                        .param("correlationId", depositId.toString())
                        .header("Authorization", bearerB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(jsonB).size()).isEqualTo(0);
    }

    // =========================================================================
    // 4. AGENT token → 403 Forbidden
    // =========================================================================

    @Test
    void auditList_asAgent_returns403() throws Exception {
        User agent = new User("agent-audit@acme.com", "hash");
        agent = userRepository.save(agent);
        String agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        mvc.perform(get(AUDIT_ENDPOINT).header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private UUID createProject() throws Exception {
        String ref = "AUDIT-PROJ-" + (++refCounter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId, String bearer) throws Exception {
        String ref = "AUDIT-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Audit Test Appt " + ref, ref,
                new BigDecimal("300000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("80"), null,
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
        var req = new CreateContactRequest("Audit", "Test", null, email, null, null, null, true, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createDeposit(UUID propertyId, UUID contactId) throws Exception {
        var req = new CreateDepositRequest(
                contactId, propertyId, new BigDecimal("15000.00"),
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

    private UUID createDraftContract(UUID projectId, UUID propertyId, UUID buyerId) throws Exception {
        var req = new CreateContractRequest(
                projectId, propertyId, buyerId, USER_ID,
                new BigDecimal("280000.00"), null, null
        );
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContractResponse.class).id();
    }
}
