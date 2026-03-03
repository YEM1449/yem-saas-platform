package com.yem.hlm.backend.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.portal.service.PortalJwtProvider;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.UserRole;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Portal contract endpoints.
 *
 * Tests:
 * 1) buyer_seesOwnContracts
 * 2) buyer_cannotSeeOtherBuyerContracts (tenant isolation)
 * 3) crmUser_cannotAccessPortalEndpoint (RBAC isolation)
 * 4) portal_tenantInfo_returnsName
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalContractsIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired PortalJwtProvider portalJwtProvider;

    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. Buyer sees own contracts
    // =========================================================================

    @Test
    void buyer_seesOwnContracts() throws Exception {
        UUID buyerId   = createContact("portal-c-buyer1@acme.com");
        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        UUID contractId = createAndSignContract(projectId, propId, buyerId,
                new BigDecimal("300000.00"));

        String portalJwt = portalJwtProvider.generate(buyerId, TENANT_ID);

        String json = mvc.perform(get("/api/portal/contracts")
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode arr = objectMapper.readTree(json);
        assertThat(arr.isArray()).isTrue();
        boolean found = false;
        for (JsonNode n : arr) {
            if (contractId.toString().equals(n.get("id").asText())) {
                found = true;
                assertThat(n.get("agreedPrice").decimalValue())
                        .isEqualByComparingTo("300000.00");
            }
        }
        assertThat(found).as("contract %s should appear in portal list", contractId).isTrue();
    }

    // =========================================================================
    // 2. Buyer cannot see another buyer's contracts
    // =========================================================================

    @Test
    void buyer_cannotSeeOtherBuyerContracts() throws Exception {
        UUID buyerA = createContact("portal-c-buyerA@acme.com");
        UUID buyerB = createContact("portal-c-buyerB@acme.com");

        UUID projectId = createProject();
        UUID propA = createAndActivateProperty(projectId);
        createAndSignContract(projectId, propA, buyerA, new BigDecimal("100000.00"));

        // BuyerB has no contracts — portal list must be empty
        String portalJwt = portalJwtProvider.generate(buyerB, TENANT_ID);

        String json = mvc.perform(get("/api/portal/contracts")
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(json).size()).isZero();
    }

    // =========================================================================
    // 3. CRM user (ROLE_ADMIN) cannot access portal endpoint
    // =========================================================================

    @Test
    void crmUser_cannotAccessPortalEndpoint() throws Exception {
        mvc.perform(get("/api/portal/contracts")
                        .header("Authorization", adminBearer))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Portal tenant-info returns tenant name
    // =========================================================================

    @Test
    void portal_tenantInfo_returnsName() throws Exception {
        UUID buyerId = createContact("portal-c-info@acme.com");
        String portalJwt = portalJwtProvider.generate(buyerId, TENANT_ID);

        mvc.perform(get("/api/portal/tenant-info")
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createContact(String email) throws Exception {
        var req = new CreateContactRequest("Portal", "Buyer", null, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class).id();
    }

    private UUID createProject() throws Exception {
        String ref = "PC-PROJ-" + (++refCounter);
        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "PC-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Portal Appt " + ref, ref,
                new BigDecimal("400000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("85"), null,
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

    private UUID createAndSignContract(UUID projectId, UUID propId, UUID buyerId,
                                       BigDecimal price) throws Exception {
        var req = new CreateContractRequest(projectId, propId, buyerId,
                USER_ID, price, null, null);
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
}
