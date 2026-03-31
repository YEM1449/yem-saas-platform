package com.yem.hlm.backend.deposit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/deposits/{id}/documents/reservation.pdf
 *
 * Covers:
 * - 200 OK + Content-Type application/pdf + %PDF magic bytes + Content-Disposition
 * - 404 for non-existent deposit
 * - 404 for cross-tenant access (tenant isolation)
 * - 401 when no token provided
 * - RBAC: AGENT can download own deposit's PDF
 * - RBAC: AGENT is denied (404) for another agent's deposit
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationPdfIT extends IntegrationTestBase {

    private static final String PDF_ENDPOINT = "/api/deposits/{id}/documents/reservation.pdf";

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired DepositRepository depositRepository;

    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setupToken() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // ===== Happy path (ADMIN) =====

    @Test
    void downloadPdf_asAdmin_returns200WithPdfContentType() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-ct@acme.com");

        mvc.perform(get(PDF_ENDPOINT, depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_asAdmin_bodyStartsWithPdfMagicBytes() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-magic@acme.com");

        byte[] body = mvc.perform(get(PDF_ENDPOINT, depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSizeGreaterThan(500);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void downloadPdf_asAdmin_hasAttachmentContentDisposition() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-disp@acme.com");

        mvc.perform(get(PDF_ENDPOINT, depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("reservation_")));
    }

    // ===== Not found =====

    @Test
    void downloadPdf_nonExistentDeposit_returns404() throws Exception {
        mvc.perform(get(PDF_ENDPOINT, UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // ===== Tenant isolation =====

    @Test
    void downloadPdf_crossTenant_returns404() throws Exception {
        UUID depositIdA = createDepositWithProperty("pdf-iso-a@acme.com");

        // Tenant B user cannot access tenant A's deposit
        String bearerB = createOtherTenantBearer("pdf-iso-b");

        mvc.perform(get(PDF_ENDPOINT, depositIdA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== Auth =====

    @Test
    void downloadPdf_withoutToken_returns401() throws Exception {
        mvc.perform(get(PDF_ENDPOINT, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== RBAC — AGENT =====

    @Test
    void downloadPdf_asAgent_ownDeposit_returns200() throws Exception {
        // Create an AGENT user in the same tenant
        User agent = new User("agent-pdf@acme.com", "hashedPass");
        agent = userRepository.save(agent);
        String agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        // Agents cannot create deposits via API (ADMIN/MANAGER only), so create directly via repository
        UUID depositId = createDepositForAgent(agent, "agent-buyer-own@acme.com");

        mvc.perform(get(PDF_ENDPOINT, depositId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_asAgent_otherAgentsDeposit_returns404() throws Exception {
        // Agent A owns a deposit
        User agentA = new User("agent-a-pdf@acme.com", "hashedPass");
        agentA = userRepository.save(agentA);

        // Agent B tries to access Agent A's deposit
        User agentB = new User("agent-b-pdf@acme.com", "hashedPass");
        agentB = userRepository.save(agentB);
        String bearerB = "Bearer " + jwtProvider.generate(agentB.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        UUID depositIdA = createDepositForAgent(agentA, "buyer-agent-a@acme.com");

        mvc.perform(get(PDF_ENDPOINT, depositIdA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== Helpers =====

    private UUID createDepositWithProperty(String contactEmail) throws Exception {
        return createDepositWithPropertyForBearer(adminBearer, contactEmail);
    }

    private UUID createDepositWithPropertyForBearer(String bearer, String contactEmail) throws Exception {
        ContactResponse contact = createContact(adminBearer, contactEmail);
        UUID propertyId = createActiveProperty(adminBearer);

        var req = new CreateDepositRequest(
                contact.id(), propertyId, new BigDecimal("5000.00"),
                null, null, "MAD", null, null
        );

        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(json, DepositResponse.class).id();
    }

    private ContactResponse createContact(String bearer, String email) throws Exception {
        var req = new CreateContactRequest("Jean", "Dupont", null, email, null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createActiveProperty(String bearer) throws Exception {
        String ref = "PDF-" + (++refCounter);
        String projectBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PDF Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projectBody).get("id").asText());

        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "PDF Villa " + ref, ref,
                new BigDecimal("2000000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("200"), new BigDecimal("400"),
                3, 2, 2, null, null, null, null, null, null, null, null, null,
                null, projId, null, null
        );

        String propJson = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse created = objectMapper.readValue(propJson, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }

    /**
     * Creates a deposit directly via repository with the given agent as the owner.
     * Used for agent-RBAC tests because POST /api/deposits requires ADMIN or MANAGER.
     */
    private UUID createDepositForAgent(User agent, String contactEmail) throws Exception {
        // Create contact via admin API
        var cReq = new CreateContactRequest("Jean", "Dupont", null, contactEmail, null, null, null, null, null, null);
        String contactJson = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(contactJson, ContactResponse.class).id();

        // Create property via admin API
        UUID propertyId = createActiveProperty(adminBearer);

        // Build and save the deposit directly with the specified agent
        Societe societe = societeRepository.findById(TENANT_ID).orElseThrow();
        var contact = contactRepository.findById(contactId).orElseThrow();
        Deposit deposit = new Deposit(societe.getId(), contact, agent);
        deposit.setPropertyId(propertyId);
        deposit.setAmount(new BigDecimal("5000.00"));
        deposit.setCurrency("MAD");
        deposit.setDepositDate(LocalDate.now());
        deposit.setReference("PDF-AGENT-" + System.nanoTime());
        deposit.setStatus(DepositStatus.PENDING);
        deposit.setDueDate(LocalDateTime.now().plusDays(7));
        return depositRepository.save(deposit).getId();
    }

    private String createOtherTenantBearer(String keyPrefix) {
        String otherKey = keyPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@" + keyPrefix + ".com", "hashedPass");
        userB = userRepository.save(userB);
        return "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);
    }
}
