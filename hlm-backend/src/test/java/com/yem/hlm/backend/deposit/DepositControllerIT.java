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
    private int refCounter = 0;

    @BeforeEach
    void setupToken() {
        // ROLE_ADMIN needed to create properties via API
        bearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // ===== Auth =====

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Core deposit flow =====

    @Test
    void create_withToken_returns201_pending_andQualifiesContact() throws Exception {
        ContactResponse contact = createContact("dep1@acme.com");
        UUID propertyId = createActiveProperty();

        var req = new CreateDepositRequest(
                contact.id(), propertyId, new BigDecimal("1000.00"),
                null, null, "MAD", null, null
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
        UUID propertyId = createActiveProperty();

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
        UUID propertyId = createActiveProperty();

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
        UUID propertyId = createActiveProperty();

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

    // ===== Property reservation locking =====

    @Test
    void firstDeposit_marksPropertyReserved() throws Exception {
        ContactResponse contact = createContact("dep-prop-res@acme.com");
        UUID propertyId = createActiveProperty();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("1500.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Verify property is now RESERVED
        PropertyResponse property = getProperty(propertyId);
        assertThat(property.status()).isEqualTo(PropertyStatus.RESERVED);
    }

    @Test
    void firstDeposit_stampsReservedAtOnProperty() throws Exception {
        ContactResponse contact = createContact("dep-reserved-at@acme.com");
        UUID propertyId = createActiveProperty();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("1200.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // reservedAt must be set
        PropertyResponse property = getProperty(propertyId);
        assertThat(property.status()).isEqualTo(PropertyStatus.RESERVED);
        assertThat(property.reservedAt()).isNotNull();
    }

    @Test
    void cancelDeposit_clearsReservedAtOnProperty() throws Exception {
        ContactResponse contact = createContact("dep-cancel-reservedat@acme.com");
        UUID propertyId = createActiveProperty();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("800.00"), null, null, "MAD", null, null);

        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        DepositResponse created = objectMapper.readValue(json, DepositResponse.class);

        // Confirm reservedAt was stamped
        assertThat(getProperty(propertyId).reservedAt()).isNotNull();

        // Cancel the deposit
        mvc.perform(post("/api/deposits/{id}/cancel", created.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        // reservedAt must be cleared and property back to ACTIVE
        PropertyResponse released = getProperty(propertyId);
        assertThat(released.status()).isEqualTo(PropertyStatus.ACTIVE);
        assertThat(released.reservedAt()).isNull();
    }

    @Test
    void cancelDeposit_releasesPropertyReservation() throws Exception {
        ContactResponse contact = createContact("dep-cancel-rel@acme.com");
        UUID propertyId = createActiveProperty();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("2000.00"), null, null, "MAD", null, null);

        String json = mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        DepositResponse created = objectMapper.readValue(json, DepositResponse.class);

        // Property is RESERVED
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);

        // Cancel the deposit
        mvc.perform(post("/api/deposits/{id}/cancel", created.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(DepositStatus.CANCELLED.name()));

        // Property is back to ACTIVE
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.ACTIVE);
    }

    @Test
    void create_onNonActiveProperty_returns409() throws Exception {
        ContactResponse contact = createContact("dep-draft@acme.com");
        // Create property in DRAFT status (don't activate it)
        UUID propertyId = createDraftProperty();

        var req = new CreateDepositRequest(contact.id(), propertyId, new BigDecimal("500.00"), null, null, "MAD", null, null);

        mvc.perform(post("/api/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ===== Tenant isolation =====

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

        // Create property in tenant B
        UUID propertyB = createActivePropertyForBearer(bearerB);

        // Tenant B tries to create deposit for tenant A's contact
        var req = new CreateDepositRequest(contactA.id(), propertyB, new BigDecimal("500.00"), null, null, "MAD", null, null);

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
        UUID propertyId = createActiveProperty();
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

    // ===== Validation =====

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

    // ===== Helpers =====

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("John", "Doe", null, email, null, null, null, null, null, null);
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

    private UUID createActiveProperty() throws Exception {
        return createActivePropertyForBearer(bearer);
    }

    private UUID createActivePropertyForBearer(String bearerToken) throws Exception {
        String ref = "DEP-TEST-" + (++refCounter);
        String projectBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projectBody).get("id").asText());
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "Test Villa " + ref, ref,
                new BigDecimal("1000000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("200"), new BigDecimal("400"),
                3, 2, 2, null, null, null, null, null, null, null, null, null,
                null, projId, null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // Activate the property (DRAFT → ACTIVE)
        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }

    private UUID createDraftProperty() throws Exception {
        String ref = "DEP-DRAFT-" + (++refCounter);
        String projectBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Draft Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projectBody).get("id").asText());
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "Draft Villa " + ref, ref,
                new BigDecimal("500000"), "MAD",
                null, null, null, "Rabat", null, null, null, null,
                null, null, null, null,
                new BigDecimal("150"), new BigDecimal("300"),
                2, 1, 1, null, null, null, null, null, null, null, null, null,
                null, projId, null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(json, PropertyResponse.class).id();
    }

    private PropertyResponse getProperty(UUID id) throws Exception {
        String json = mvc.perform(get("/api/properties/{id}", id)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PropertyResponse.class);
    }
}
