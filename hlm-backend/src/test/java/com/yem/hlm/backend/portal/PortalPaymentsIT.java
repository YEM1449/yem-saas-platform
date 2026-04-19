package com.yem.hlm.backend.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;
import com.yem.hlm.backend.portal.service.PortalJwtProvider;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.vente.api.dto.VenteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Portal property access.
 *
 * The payment-schedule endpoint was removed (écheancier is now in the ventes tab).
 * Property access now requires a Vente for the authenticated buyer (not a SaleContract).
 *
 * Tests:
 * 1) buyer_seesOwnProperty
 * 2) buyer_cannotSeeOtherBuyerProperty (returns 404)
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalPaymentsIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired PortalJwtProvider portalJwtProvider;

    private String adminBearer;
    private String uid;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. Buyer sees own property (via Vente ownership)
    // =========================================================================

    @Test
    void buyer_seesOwnProperty() throws Exception {
        UUID buyerId   = createContact("portal-p-prop1-" + uid + "@acme.com");
        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        createVente(propId, buyerId, new BigDecimal("350000.00"));

        String portalJwt = portalJwtProvider.generate(buyerId, TENANT_ID);

        mvc.perform(get("/api/portal/properties/{id}", propId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propId.toString()));
    }

    // =========================================================================
    // 2. Buyer cannot see another buyer's property (→ 404)
    // =========================================================================

    @Test
    void buyer_cannotSeeOtherBuyerProperty() throws Exception {
        UUID buyerA = createContact("portal-p-propA-" + uid + "@acme.com");
        UUID buyerB = createContact("portal-p-propB-" + uid + "@acme.com");

        UUID projectId = createProject();
        UUID propId    = createAndActivateProperty(projectId);
        createVente(propId, buyerA, new BigDecimal("250000.00"));

        // BuyerB tries to access buyerA's property — no Vente for buyerB → 404
        String portalJwt = portalJwtProvider.generate(buyerB, TENANT_ID);

        mvc.perform(get("/api/portal/properties/{id}", propId)
                        .header("Authorization", "Bearer " + portalJwt))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createContact(String email) throws Exception {
        var req = new CreateContactRequest("Portal", "Buyer", null, email,
                null, null, null, false, null, ProcessingBasis.CONTRACT);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class).id();
    }

    private UUID createProject() throws Exception {
        String ref = "PP-PROJ-" + uid + "-" + (++refCounter);
        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "PP-PROP-" + uid + "-" + (++refCounter);
        var req = new PropertyCreateRequest(
                PropertyType.APPARTEMENT, "Portal Pay Appt " + ref, ref,
                new BigDecimal("450000"), "MAD",
                null, null, null, "Rabat", null, null, null, null,
                null, null, null, null,
                new BigDecimal("90"), null,
                2, 1, 0, null, null, null, null, 1, null, null, null, null,
                null, projectId, null, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse prop = objectMapper.readValue(json, PropertyResponse.class);
        mvc.perform(patch("/api/properties/{id}/status", prop.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return prop.id();
    }

    private UUID createVente(UUID propId, UUID buyerId, BigDecimal price) throws Exception {
        String body = "{\"contactId\":\"" + buyerId + "\",\"propertyId\":\"" + propId
                + "\",\"prixVente\":" + price.toPlainString()
                + ",\"dateCompromis\":\"2026-04-01\"}";
        String json = mvc.perform(post("/api/ventes")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, VenteResponse.class).id();
    }
}
