package com.yem.hlm.backend.contract.api;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/contracts/{id}/documents/contract.pdf
 *
 * Covers:
 * - 200 OK + Content-Type application/pdf + %PDF magic bytes + Content-Disposition
 * - 404 for non-existent contract
 * - 404 for cross-tenant access (tenant isolation)
 * - 401 when no token provided
 * - AGENT can download own contract's PDF
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContractPdfIT extends IntegrationTestBase {

    private static final String PDF_ENDPOINT = "/api/contracts/{id}/documents/contract.pdf";

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
    void setupToken() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // ===== Happy path (ADMIN) =====

    @Test
    void downloadPdf_asAdmin_returns200WithPdfContentType() throws Exception {
        UUID contractId = createDraftContract(adminBearer, "pdf-ct@acme.com");

        mvc.perform(get(PDF_ENDPOINT, contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_asAdmin_bodyStartsWithPdfMagicBytes() throws Exception {
        UUID contractId = createDraftContract(adminBearer, "pdf-magic@acme.com");

        byte[] body = mvc.perform(get(PDF_ENDPOINT, contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSizeGreaterThan(500);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void downloadPdf_asAdmin_hasAttachmentContentDisposition() throws Exception {
        UUID contractId = createDraftContract(adminBearer, "pdf-disp@acme.com");

        mvc.perform(get(PDF_ENDPOINT, contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("contract_")));
    }

    // ===== Not found =====

    @Test
    void downloadPdf_nonExistentContract_returns404() throws Exception {
        mvc.perform(get(PDF_ENDPOINT, UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // ===== Tenant isolation =====

    @Test
    void downloadPdf_crossTenant_returns404() throws Exception {
        UUID contractIdA = createDraftContract(adminBearer, "pdf-iso-a@acme.com");

        // Tenant B user cannot access tenant A's contract
        String bearerB = createOtherTenantBearer("cpdf-iso-b");

        mvc.perform(get(PDF_ENDPOINT, contractIdA)
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
    void downloadPdf_asAgent_ownContract_returns200() throws Exception {
        // Create an AGENT user in the same tenant
        User agent = new User("agent-cpdf@acme.com", "hashedPass");
        agent = userRepository.save(agent);
        String agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        // Agent creates the contract — agentId auto-assigned to self
        UUID contractId = createDraftContract(agentBearer, "buyer-agent-cpdf@acme.com");

        mvc.perform(get(PDF_ENDPOINT, contractId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ===== Helpers =====

    private UUID createDraftContract(String bearer, String buyerEmail) throws Exception {
        String ref = "CPDF-" + (++refCounter);

        // Project — always use adminBearer; agents cannot create projects
        String projBody = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CPdf Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projBody).get("id").asText());

        // Property (DRAFT → ACTIVE) — always use adminBearer
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "CPdf Villa " + ref, ref,
                new BigDecimal("1800000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("150"), new BigDecimal("300"),
                3, 2, 1, null, null, null, null, null, null, null, null, null,
                null, projId, null, null
        );
        String propBody = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse prop = objectMapper.readValue(propBody, PropertyResponse.class);

        mvc.perform(put("/api/properties/{id}", prop.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                                        null, null, null, null))))
                .andExpect(status().isOk());

        // Contact (buyer) — always use adminBearer
        var cReq = new CreateContactRequest("Marie", "Curie", null, buyerEmail, null, null, null, null, null, null);
        String cBody = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID buyerId = objectMapper.readValue(cBody, ContactResponse.class).id();

        // Derive agentId: for AGENT token, USER_ID is the admin, so we need to extract from token
        // For ADMIN: explicitly provide USER_ID; for AGENT: provide null (service auto-assigns)
        boolean isAdmin = bearer.equals(adminBearer);
        UUID agentId = isAdmin ? USER_ID : null;

        var ctrReq = new CreateContractRequest(
                projId, prop.id(), buyerId, agentId,
                new BigDecimal("1750000.00"), new BigDecimal("1800000.00"), null
        );
        String ctrBody = mvc.perform(post("/api/contracts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctrReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(ctrBody, ContractResponse.class).id();
    }

    private String createOtherTenantBearer(String keyPrefix) {
        String otherKey = keyPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@" + keyPrefix + ".com", "hashedPass");
        userB = userRepository.save(userB);
        return "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);
    }
}
