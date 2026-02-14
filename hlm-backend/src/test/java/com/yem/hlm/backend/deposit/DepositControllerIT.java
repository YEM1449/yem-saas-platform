package com.yem.hlm.backend.deposit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DepositControllerIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    private String bearer;

    @BeforeEach
    void setupToken() {
        bearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withToken_returns201_pending_andQualifiesContact() throws Exception {
        ContactResponse contact = createContact("dep1@acme.com");
        UUID propertyId = UUID.randomUUID();

        var req = new CreateDepositRequest(
                contact.id(),
                propertyId,
                new BigDecimal("1000.00"),
                null,
                null,
                "MAD",
                null,
                null
        );

        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value(DepositStatus.PENDING.name()))
                .andExpect(jsonPath("$.contactId").value(contact.id().toString()))
                .andExpect(jsonPath("$.propertyId").value(propertyId.toString()))
                .andReturn().getResponse().getContentAsString();

        DepositResponse createdDeposit = objectMapper.readValue(json, DepositResponse.class);
        assertThat(createdDeposit.amount()).isEqualByComparingTo("1000.00");
        assertThat(createdDeposit.currency()).isEqualTo("MAD");

        // Contact becomes TEMP_CLIENT (reservation) and qualified
        ContactResponse refreshed = getContact(contact.id());
        assertThat(refreshed.qualified()).isTrue();
        assertThat(refreshed.status()).isEqualTo(ContactStatus.QUALIFIED_PROSPECT);
        assertThat(refreshed.contactType()).isEqualTo(ContactType.TEMP_CLIENT);
        assertThat(refreshed.tempClientUntil()).isNotNull();
    }

    @Test
    void create_duplicate_sameContactAndProperty_returns409() throws Exception {
        ContactResponse contact = createContact("depdup@acme.com");
        UUID propertyId = UUID.randomUUID();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("500.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_whenPropertyAlreadyReserved_returns409() throws Exception {
        UUID propertyId = UUID.randomUUID();

        ContactResponse c1 = createContact("dep-res-1@acme.com");
        ContactResponse c2 = createContact("dep-res-2@acme.com");

        var req1 = new CreateDepositRequest(c1.id(), propertyId, new BigDecimal("900.00"), null, null, "MAD", null, null);
        var req2 = new CreateDepositRequest(c2.id(), propertyId, new BigDecimal("1000.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_deposit_convertsContactToClient() throws Exception {
        ContactResponse contact = createContact("depconfirm@acme.com");
        UUID propertyId = UUID.randomUUID();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("2500.00"), null, null, "MAD", null, null);

        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        DepositResponse created = objectMapper.readValue(json, DepositResponse.class);

        mvc.perform(post("/api/deposits/{id}/confirm", created.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(DepositStatus.CONFIRMED.name()));

        ContactResponse refreshed = getContact(contact.id());
        assertThat(refreshed.contactType()).isEqualTo(ContactType.CLIENT);
        assertThat(refreshed.status()).isEqualTo(ContactStatus.CLIENT);
        assertThat(refreshed.tempClientUntil()).isNull();
    }

    @Test
    void create_crossTenantContact_returns404() throws Exception {
        // Create contact in tenant A
        ContactResponse contactA = createContact("dep-cross@acme.com");

        // Create tenant B + user
        String otherKey = "dep-iso-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantB = tenantRepository.save(new Tenant(otherKey, "Deposit Isolation Tenant"));
        User userB = new User(tenantB, "admin@dep-iso.com", "hashedPass");
        userB.setRole(UserRole.ROLE_ADMIN);
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Tenant B tries to create deposit for tenant A's contact
        var req = new CreateDepositRequest(contactA.id(), UUID.randomUUID(), new BigDecimal("500.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void report_tenantIsolation_returnsOnlyOwnDeposits() throws Exception {
        // Create deposit in tenant A
        ContactResponse contactA = createContact("dep-iso-report@acme.com");
        UUID propertyId = UUID.randomUUID();
        var req = new CreateDepositRequest(contactA.id(), propertyId, new BigDecimal("750.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Create tenant B + user
        String otherKey = "dep-rpt-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantB = tenantRepository.save(new Tenant(otherKey, "Report Isolation Tenant"));
        User userB = new User(tenantB, "admin@dep-rpt.com", "hashedPass");
        userB.setRole(UserRole.ROLE_ADMIN);
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Tenant B's report must be empty
        mvc.perform(get("/api/deposits/report")
                        .header("Authorization", bearerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void create_invalidAmount_returns400() throws Exception {
        ContactResponse contact = createContact("dep-badamt@acme.com");

        var req = new CreateDepositRequest(contact.id(), UUID.randomUUID(), new BigDecimal("-100.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("John", "Doe", null, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private ContactResponse getContact(UUID id) throws Exception {
        String json = mvc.perform(get("/api/contacts/{id}", id)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }
}
