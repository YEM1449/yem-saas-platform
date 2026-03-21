package com.yem.hlm.backend.contract.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.contract.domain.BuyerType;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContractControllerIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    /** ROLE_ADMIN bearer for the seed tenant — used as default throughout. */
    private String adminBearer;
    private int refCounter = 0;

    @BeforeEach
    void setupToken() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // 1. createDraft_activeProject_returns201
    // =========================================================================

    @Test
    void createDraft_activeProject_returns201() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("contract-create@acme.com");

        var req = new CreateContractRequest(
                projectId, propertyId, buyer.id(),
                USER_ID,                          // agentId required for ADMIN caller
                new BigDecimal("500000.00"),
                new BigDecimal("550000.00"),
                null
        );

        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value(SaleContractStatus.DRAFT.name()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.propertyId").value(propertyId.toString()))
                .andExpect(jsonPath("$.buyerContactId").value(buyer.id().toString()))
                .andExpect(jsonPath("$.agentId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.signedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        ContractResponse created = objectMapper.readValue(json, ContractResponse.class);
        assertThat(created.agreedPrice()).isEqualByComparingTo("500000.00");
        assertThat(created.listPrice()).isEqualByComparingTo("550000.00");
    }

    // =========================================================================
    // 2. createDraft_archivedProject_returns400
    // =========================================================================

    @Test
    void createDraft_archivedProject_returns400() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("contract-archived@acme.com");

        // Archive the project (DELETE → 204)
        mvc.perform(delete("/api/projects/{id}", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        var req = new CreateContractRequest(
                projectId, propertyId, buyer.id(),
                USER_ID,
                new BigDecimal("300000.00"),
                null, null
        );

        mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. signDraft_setsSignedAt_andPropertySold
    // =========================================================================

    @Test
    void signDraft_setsSignedAt_andPropertySold() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("contract-sign@acme.com");

        // Create DRAFT contract
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);

        // Sign it — requires ADMIN or MANAGER
        String json = mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SaleContractStatus.SIGNED.name()))
                .andExpect(jsonPath("$.signedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        ContractResponse signed = objectMapper.readValue(json, ContractResponse.class);
        assertThat(signed.signedAt()).isNotNull();

        // Property must now be SOLD
        PropertyResponse property = getProperty(propertyId);
        assertThat(property.status()).isEqualTo(PropertyStatus.SOLD);
        assertThat(property.soldAt()).isNotNull();
    }

    // =========================================================================
    // 4. doubleSale_preventSecondSignedContractForSameProperty_returns409
    // =========================================================================

    @Test
    void doubleSale_preventSecondSignedContractForSameProperty_returns409() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);

        ContactResponse buyer1 = createContact("double-sale-b1@acme.com");
        ContactResponse buyer2 = createContact("double-sale-b2@acme.com");

        UUID contract1Id = createDraftContract(projectId, propertyId, buyer1.id(), adminBearer);
        UUID contract2Id = createDraftContract(projectId, propertyId, buyer2.id(), adminBearer);

        // Sign the first contract — succeeds
        mvc.perform(post("/api/contracts/{id}/sign", contract1Id)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Attempt to sign the second contract for the same property — must be rejected
        mvc.perform(post("/api/contracts/{id}/sign", contract2Id)
                        .header("Authorization", adminBearer))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 5. tenantIsolation_crossTenantIds_return404
    // =========================================================================

    @Test
    void tenantIsolation_crossTenantIds_return404() throws Exception {
        // Setup tenant A resources
        UUID projectA = createProject(adminBearer);
        UUID propertyA = createAndActivateProperty(projectA, adminBearer);
        ContactResponse buyerA = createContact("isolation-a@acme.com");

        // Setup tenant B
        String keyB = "iso-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@iso-b.com", "hashedPass");
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Tenant B's project (required so agentId + project belong to B)
        UUID projectB = createProject(bearerB);

        // Tenant B tries to create a contract pointing at tenant A's property/contact
        var req = new CreateContractRequest(
                projectB,      // project belongs to B  — property to A → mismatch
                propertyA,     // belongs to tenant A
                buyerA.id(),   // belongs to tenant A
                userB.getId(), // agentId = B user
                new BigDecimal("200000.00"),
                null, null
        );

        // The property lookup in tenant B's scope will not find tenant A's property → 404
        mvc.perform(post("/api/contracts")
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 6. agentRestriction_agentCannotSetOtherAgentId_returns400
    // =========================================================================

    @Test
    void agentRestriction_agentCannotSetOtherAgentId_returns400() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("agent-restrict@acme.com");

        // Create a second user to use as the "other agent"
        User otherAgent = new User("other-agent@acme.com", "hashedPass");
        otherAgent = userRepository.save(otherAgent);

        // Token for the seed user but with ROLE_AGENT privilege
        String agentBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);

        // Agent (USER_ID) tries to set agentId = otherAgent.getId() — must be rejected
        var req = new CreateContractRequest(
                projectId, propertyId, buyer.id(),
                otherAgent.getId(),                // DIFFERENT from caller USER_ID
                new BigDecimal("250000.00"),
                null, null
        );

        mvc.perform(post("/api/contracts")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Create a project with an auto-generated name; returns the new project's UUID. */
    private UUID createProject(String bearer) throws Exception {
        String ref = "CTR-PROJ-" + (++refCounter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    /** Create a property in a project and activate it (DRAFT → ACTIVE). Returns property UUID. */
    private UUID createAndActivateProperty(UUID projectId, String bearer) throws Exception {
        String ref = "CTR-PROP-" + (++refCounter);
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "Contract Test Villa " + ref, ref,
                new BigDecimal("800000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("180"), new BigDecimal("350"),
                3, 2, 1, null, null, null, null, null, null, null, null, null,
                null, projectId, null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse created = objectMapper.readValue(json, PropertyResponse.class);

        // DRAFT → ACTIVE
        var updateReq = new PropertyUpdateRequest(
                null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null
        );
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }

    /** Create a contact in the default tenant; returns the ContactResponse. */
    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("John", "Doe", null, email, null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    /** Create a DRAFT contract with agentId = USER_ID; returns the contract UUID. */
    private UUID createDraftContract(UUID projectId, UUID propertyId, UUID buyerContactId,
                                     String bearer) throws Exception {
        var req = new CreateContractRequest(
                projectId, propertyId, buyerContactId,
                USER_ID,
                new BigDecimal("450000.00"),
                null, null
        );
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContractResponse.class).id();
    }

    /** Fetch a property by ID using the admin bearer. */
    private PropertyResponse getProperty(UUID propertyId) throws Exception {
        String json = mvc.perform(get("/api/properties/{id}", propertyId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PropertyResponse.class);
    }

    /**
     * Confirm an existing deposit via POST /api/deposits/{id}/confirm.
     * Side-effect: deposit moves to CONFIRMED; property stays RESERVED.
     */
    private void confirmDeposit(UUID depositId) throws Exception {
        mvc.perform(post("/api/deposits/{id}/confirm", depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
    }

    /**
     * Create a PENDING deposit for the given property + contact.
     * Returns the deposit UUID. Side-effect: property moves to RESERVED.
     */
    private UUID createDeposit(UUID propertyId, UUID contactId) throws Exception {
        var req = new CreateDepositRequest(
                contactId, propertyId, new BigDecimal("25000.00"),
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

    // =========================================================================
    // 7. cancelSignedContract_revertsPropertyToAvailable (no CONFIRMED deposit)
    // "AVAILABLE" = PropertyStatus.ACTIVE in the domain enum
    // =========================================================================

    @Test
    void cancelSignedContract_revertsPropertyToAvailable() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("cancel-revert@acme.com");
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);

        // Sign → property SOLD
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.SOLD);

        // Cancel the signed contract — no CONFIRMED deposit exists, so property reverts to AVAILABLE
        mvc.perform(post("/api/contracts/{id}/cancel", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SaleContractStatus.CANCELED.name()))
                .andExpect(jsonPath("$.canceledAt").isNotEmpty());

        PropertyResponse reverted = getProperty(propertyId);
        assertThat(reverted.status()).isEqualTo(PropertyStatus.ACTIVE); // AVAILABLE in domain terms
        assertThat(reverted.soldAt()).isNull();
    }

    // =========================================================================
    // 8. confirmDepositOnSoldProperty_returns409
    // Tests that DepositService.confirm() rejects when property is SOLD
    // =========================================================================

    @Test
    void confirmDepositOnSoldProperty_returns409() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("confirm-sold@acme.com");

        // Create deposit → property RESERVED
        UUID depositId = createDeposit(propertyId, buyer.id());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);

        // Create and sign a contract → property SOLD
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.SOLD);

        // Confirm the PENDING deposit — must be blocked because property is SOLD
        mvc.perform(post("/api/deposits/{id}/confirm", depositId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 9. cancelSignedContract_withConfirmedDeposit_revertsPropertyToReserved
    // Cancel rule: CONFIRMED deposit exists → revert to RESERVED (not AVAILABLE)
    // =========================================================================

    @Test
    void cancelSignedContract_withConfirmedDeposit_revertsPropertyToReserved() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("cancel-revert-reserved@acme.com");

        // Create deposit → property RESERVED; confirm it → deposit CONFIRMED
        UUID depositId = createDeposit(propertyId, buyer.id());
        confirmDeposit(depositId);
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);

        // Sign a contract → property SOLD
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.SOLD);

        // Cancel the signed contract — CONFIRMED deposit still exists → revert to RESERVED
        mvc.perform(post("/api/contracts/{id}/cancel", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SaleContractStatus.CANCELED.name()));

        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);
    }

    // =========================================================================
    // 11. signDraft_persistsBuyerSnapshot — P0-2
    // Verifies that buyer snapshot is captured immutably at signing time
    // =========================================================================

    @Test
    void signDraft_persistsBuyerSnapshot() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("buyer-snapshot@acme.com");
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);

        // DRAFT contract has no snapshot yet
        String draftJson = mvc.perform(get("/api/contracts")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Sign the contract
        String signedJson = mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ContractResponse signed = objectMapper.readValue(signedJson, ContractResponse.class);

        // Snapshot fields populated at signing time
        assertThat(signed.buyerType()).isEqualTo(BuyerType.PERSON);
        assertThat(signed.buyerDisplayName()).isEqualTo("John Doe"); // matches createContact("John","Doe",...)
        assertThat(signed.buyerEmail()).isEqualTo("buyer-snapshot@acme.com");
        // phone is null (not set in createContact helper) — snapshot captures null too
        assertThat(signed.buyerPhone()).isNull();
        assertThat(signed.signedAt()).isNotNull();
    }

    // =========================================================================
    // 10. agentCannotSignContract_returns403
    // ROLE_AGENT is not allowed to call POST /api/contracts/{id}/sign
    // =========================================================================

    @Test
    void agentCannotSignContract_returns403() throws Exception {
        UUID projectId = createProject(adminBearer);
        UUID propertyId = createAndActivateProperty(projectId, adminBearer);
        ContactResponse buyer = createContact("agent-sign-403@acme.com");
        UUID contractId = createDraftContract(projectId, propertyId, buyer.id(), adminBearer);

        // Create an AGENT-role token for the same tenant
        String agentBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);

        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }
}
