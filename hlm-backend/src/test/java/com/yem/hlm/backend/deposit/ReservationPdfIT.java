package com.yem.hlm.backend.deposit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/deposits/{id}/pdf
 *
 * Verifies:
 * - 200 + Content-Type application/pdf for a valid deposit
 * - PDF body starts with %PDF and has a meaningful size
 * - 404 for a non-existent deposit ID
 * - 404 for a cross-tenant access attempt (tenant isolation)
 * - 401 when no token is provided
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationPdfIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private String bearer;
    private int refCounter = 0;

    @BeforeEach
    void setupToken() {
        bearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // ===== Happy path =====

    @Test
    void downloadPdf_validDeposit_returns200WithPdfContentType() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-happy@acme.com");

        mvc.perform(get("/api/deposits/{id}/pdf", depositId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_validDeposit_bodyStartsWithPdfMagicBytes() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-magic@acme.com");

        byte[] body = mvc.perform(get("/api/deposits/{id}/pdf", depositId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSizeGreaterThan(500);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void downloadPdf_validDeposit_hasContentDispositionAttachment() throws Exception {
        UUID depositId = createDepositWithProperty("pdf-disp@acme.com");

        mvc.perform(get("/api/deposits/{id}/pdf", depositId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("reservation_")));
    }

    // ===== Not found =====

    @Test
    void downloadPdf_nonExistentDeposit_returns404() throws Exception {
        mvc.perform(get("/api/deposits/{id}/pdf", UUID.randomUUID())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    // ===== Tenant isolation =====

    @Test
    void downloadPdf_crossTenant_returns404() throws Exception {
        // Deposit created in tenant A
        UUID depositId = createDepositWithProperty("pdf-iso@acme.com");

        // Tenant B user cannot access tenant A's deposit
        String otherKey = "pdf-iso-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantB = tenantRepository.save(new Tenant(otherKey, "PDF Isolation Tenant"));
        User userB = new User(tenantB, "admin@pdf-iso.com", "hashedPass");
        userB.setRole(UserRole.ROLE_ADMIN);
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/deposits/{id}/pdf", depositId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== Auth =====

    @Test
    void downloadPdf_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/deposits/{id}/pdf", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== Helpers =====

    /** Creates a deposit (PENDING) for a freshly created contact + active property. Returns deposit ID. */
    private UUID createDepositWithProperty(String contactEmail) throws Exception {
        ContactResponse contact = createContact(contactEmail);
        UUID propertyId = createActiveProperty();

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

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Jean", "Dupont", null, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createActiveProperty() throws Exception {
        String ref = "PDF-TEST-" + (++refCounter);
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
                new BigDecimal("100"), new BigDecimal("200"),
                2, 1, 1, null, null, null, null, null, null, null, null, null,
                null, projId, null
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
                null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }
}
