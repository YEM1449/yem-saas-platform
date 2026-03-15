package com.yem.hlm.backend.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
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

import com.yem.hlm.backend.contact.api.dto.UpdateContactRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant isolation tests.
 * Two tenants (A and B); property created in tenant A; tenant B attempts
 * interest/deposit on that property → must fail 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossTenantIsolationIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private String bearerA;
    private String bearerB;
    private UUID propertyA;
    private UUID contactB;

    private int refCounter = 0;

    @BeforeEach
    void setup() throws Exception {
        // ── Tenant A ──
        Tenant tenantA = tenantRepository.save(new Tenant("iso-a-" + UUID.randomUUID().toString().substring(0, 8), "Tenant A"));
        User userA = new User(tenantA, "admin@tenant-a.test", "hash");
        userA.setRole(UserRole.ROLE_ADMIN);
        userA = userRepository.save(userA);
        bearerA = "Bearer " + jwtProvider.generate(userA.getId(), tenantA.getId(), UserRole.ROLE_ADMIN);

        // ── Tenant B ──
        Tenant tenantB = tenantRepository.save(new Tenant("iso-b-" + UUID.randomUUID().toString().substring(0, 8), "Tenant B"));
        User userB = new User(tenantB, "admin@tenant-b.test", "hash");
        userB.setRole(UserRole.ROLE_ADMIN);
        userB = userRepository.save(userB);
        bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Property in tenant A (ACTIVE)
        propertyA = createActivePropertyAs(bearerA);

        // Contact in tenant B
        contactB = createContactAs(bearerB, "contact@tenant-b.test");
    }

    // ===== Interest: cross-tenant property =====

    @Test
    void addInterest_crossTenantProperty_returns404() throws Exception {
        // Tenant B's contact tries to add interest on tenant A's property → 404
        mvc.perform(post("/api/contacts/{id}/interests", contactB)
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":\"" + propertyA + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ===== Deposit: cross-tenant property =====

    @Test
    void createDeposit_crossTenantProperty_returns404() throws Exception {
        // Tenant B tries to create deposit for their own contact but with tenant A's property → 404
        var req = new CreateDepositRequest(
                contactB, propertyA, new BigDecimal("5000.00"),
                null, null, "MAD", null, null
        );

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ===== Interest: cross-tenant contact =====

    @Test
    void addInterest_crossTenantContact_returns404() throws Exception {
        // Tenant A creates a contact
        UUID contactA = createContactAs(bearerA, "contact@tenant-a.test");
        // Tenant B's property
        UUID propertyB = createActivePropertyAs(bearerB);

        // Tenant B tries to add interest to tenant A's contact → 404
        mvc.perform(post("/api/contacts/{id}/interests", contactA)
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":\"" + propertyB + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ===== GET isolation =====

    @Test
    void getContact_crossTenant_returns404() throws Exception {
        UUID contactA = createContactAs(bearerA, "get-cross@tenant-a.test");

        // Tenant B tries to GET tenant A's contact → 404
        mvc.perform(get("/api/contacts/{id}", contactA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProperty_crossTenant_returns404() throws Exception {
        // Tenant B tries to GET tenant A's property → 404
        mvc.perform(get("/api/properties/{id}", propertyA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== GET deposit isolation =====

    @Test
    void getDeposit_crossTenant_returns404() throws Exception {
        // Create contact in tenant A, then deposit in tenant A
        UUID contactA = createContactAs(bearerA, "deposit-owner@tenant-a.test");
        UUID depositA = createDepositAs(bearerA, contactA, propertyA);

        // Tenant B tries to GET tenant A's deposit → 404
        mvc.perform(get("/api/deposits/{id}", depositA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== Confirm deposit isolation =====

    @Test
    void confirmDeposit_crossTenant_returns404() throws Exception {
        UUID contactA = createContactAs(bearerA, "dep-confirm@tenant-a.test");
        UUID depositA = createDepositAs(bearerA, contactA, propertyA);

        // Tenant B tries to confirm tenant A's deposit → 404
        mvc.perform(post("/api/deposits/{id}/confirm", depositA)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== UPDATE contact isolation =====

    @Test
    void updateContact_crossTenant_returns404() throws Exception {
        UUID contactA = createContactAs(bearerA, "upd-cross@tenant-a.test");

        // Tenant B tries to update tenant A's contact → 404
        var req = new UpdateContactRequest("Hacked", null, null, null, null, null, null, null, null, null);
        mvc.perform(patch("/api/contacts/{id}", contactA)
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ===== UPDATE property isolation =====

    @Test
    void updateProperty_crossTenant_returns404() throws Exception {
        // Tenant B tries to update tenant A's property → 404
        var req = new PropertyUpdateRequest(null, "Hacked", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        mvc.perform(put("/api/properties/{id}", propertyA)
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ===== Helpers =====

    private UUID createContactAs(String bearer, String email) throws Exception {
        var req = new CreateContactRequest("Cross", "Tenant", null, email, null, null, null, null, null, null);
        String body = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText().transform(UUID::fromString);
    }

    private UUID createActivePropertyAs(String bearer) throws Exception {
        String ref = "XISO-" + (++refCounter);
        String projectBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(json.readTree(projectBody).get("id").asText());
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "XTenant Villa " + ref, ref,
                new BigDecimal("1000000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("200"), new BigDecimal("400"),
                3, 2, 2, null, null, null, null, null, null, null, null, null,
                null, projId, null
        );

        String body = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse created = json.readValue(body, PropertyResponse.class);

        // Activate: DRAFT → ACTIVE
        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }

    private UUID createDepositAs(String bearer, UUID contactId, UUID propertyId) throws Exception {
        var req = new CreateDepositRequest(
                contactId, propertyId, new BigDecimal("5000.00"),
                null, null, "MAD", null, null
        );
        String body = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText().transform(UUID::fromString);
    }
}
