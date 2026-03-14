package com.yem.hlm.backend.portal;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Portal payment-schedule endpoints.
 *
 * Tests:
 * 1) buyer_seesOwnPaymentSchedule
 * 2) buyer_cannotSeeOtherBuyerSchedule (returns 404)
 * 3) buyer_seesOwnProperty
 * 4) buyer_cannotSeeOtherBuyerProperty (returns 404)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalPaymentsIT extends IntegrationTestBase {

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
    // 1. Buyer sees own payment schedule
    // =========================================================================

    @Test
    void buyer_seesOwnPaymentSchedule() throws Exception {
        UUID buyerId    = createContact("portal-p-buyer1@acme.com");
        UUID projectId  = createProject();
        UUID propId     = createAndActivateProperty(projectId);
        UUID contractId = createAndSignContract(projectId, propId, buyerId,
                new BigDecimal("500000.00"));

        // Create a payment schedule for the contract
        createPaymentSchedule(contractId);

        String portalJwt = portalJwtProvider.generate(buyerId, TENANT_ID);

        mvc.perform(get("/api/portal/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractId").value(contractId.toString()));
    }

    // =========================================================================
    // 2. Buyer cannot see another buyer's payment schedule (→ 404)
    // =========================================================================

    @Test
    void buyer_cannotSeeOtherBuyerSchedule() throws Exception {
        UUID buyerA = createContact("portal-p-buyerA@acme.com");
        UUID buyerB = createContact("portal-p-buyerB@acme.com");

        UUID projectId  = createProject();
        UUID propId     = createAndActivateProperty(projectId);
        UUID contractId = createAndSignContract(projectId, propId, buyerA,
                new BigDecimal("200000.00"));

        createPaymentSchedule(contractId);

        // BuyerB tries to access buyerA's schedule
        String portalJwt = portalJwtProvider.generate(buyerB, TENANT_ID);

        mvc.perform(get("/api/portal/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. Buyer sees own property
    // =========================================================================

    @Test
    void buyer_seesOwnProperty() throws Exception {
        UUID buyerId   = createContact("portal-p-prop1@acme.com");
        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        createAndSignContract(projectId, propId, buyerId, new BigDecimal("350000.00"));

        String portalJwt = portalJwtProvider.generate(buyerId, TENANT_ID);

        mvc.perform(get("/api/portal/properties/{id}", propId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propId.toString()));
    }

    // =========================================================================
    // 4. Buyer cannot see another buyer's property (→ 404)
    // =========================================================================

    @Test
    void buyer_cannotSeeOtherBuyerProperty() throws Exception {
        UUID buyerA = createContact("portal-p-propA@acme.com");
        UUID buyerB = createContact("portal-p-propB@acme.com");

        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        createAndSignContract(projectId, propId, buyerA, new BigDecimal("250000.00"));

        // BuyerB tries to access buyerA's property
        String portalJwt = portalJwtProvider.generate(buyerB, TENANT_ID);

        mvc.perform(get("/api/portal/properties/{id}", propId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isNotFound());
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
        String ref = "PP-PROJ-" + (++refCounter);
        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "PP-PROP-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Portal Pay Appt " + ref, ref,
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

    /**
     * Creates a single payment schedule item for the given contract.
     */
    private void createPaymentSchedule(UUID contractId) throws Exception {
        String body = """
                {
                  "label": "Full payment",
                  "amount": 500000.00,
                  "dueDate": "2025-12-31"
                }
                """;
        mvc.perform(post("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
