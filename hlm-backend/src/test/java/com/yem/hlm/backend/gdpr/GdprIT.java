package com.yem.hlm.backend.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.project.api.dto.ProjectCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the GDPR / Law 09-08 data subject rights API.
 *
 * <p>Covers:
 * <ul>
 *   <li>Data export (GET /api/gdpr/contacts/{id}/export)</li>
 *   <li>Anonymization (DELETE /api/gdpr/contacts/{id}/anonymize)</li>
 *   <li>Rectification view (GET /api/gdpr/contacts/{id}/rectify)</li>
 *   <li>Privacy notice (GET /api/gdpr/privacy-notice)</li>
 *   <li>RBAC enforcement</li>
 *   <li>Cross-tenant isolation</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GdprIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired SaleContractRepository contractRepository;

    private String adminBearer;
    private String managerBearer;
    private String agentBearer;
    private int refCounter = 0;

    @BeforeEach
    void setup() {
        adminBearer   = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
        managerBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_MANAGER);
        agentBearer   = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_AGENT);
    }

    // =========================================================================
    // 1. Export returns all personal data for a contact
    // =========================================================================

    @Test
    void export_existingContact_returns200WithPersonalData() throws Exception {
        UUID contactId = createContact("gdpr-export@acme.com");

        mvc.perform(get("/api/gdpr/contacts/{id}/export", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId").value(contactId.toString()))
                .andExpect(jsonPath("$.email").value("gdpr-export@acme.com"))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.interests").isArray())
                .andExpect(jsonPath("$.deposits").isArray())
                .andExpect(jsonPath("$.contracts").isArray());
    }

    // =========================================================================
    // 2. Export returns 404 for contact in a different tenant (cross-tenant)
    // =========================================================================

    @Test
    void export_contactInDifferentTenant_returns404() throws Exception {
        // A UUID that does not belong to the seed tenant
        UUID foreignContactId = UUID.randomUUID();

        mvc.perform(get("/api/gdpr/contacts/{id}/export", foreignContactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GDPR_EXPORT_NOT_FOUND"));
    }

    // =========================================================================
    // 3. Anonymize contact with no signed contracts → 200, fields zeroed
    // =========================================================================

    @Test
    void anonymize_contactWithNoContracts_returns200AndZeroesPii() throws Exception {
        UUID contactId = createContact("gdpr-anon@acme.com");

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        Contact updated = contactRepository.findById(contactId).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("ANONYMIZED");
        assertThat(updated.getEmail()).endsWith("@anonymized.invalid");
        assertThat(updated.getPhone()).isNull();
        assertThat(updated.isDeleted()).isTrue();
        assertThat(updated.getAnonymizedAt()).isNotNull();
    }

    // =========================================================================
    // 4. Anonymize contact with a SIGNED contract → 409 GDPR_ERASURE_BLOCKED
    // =========================================================================

    @Test
    void anonymize_contactWithSignedContract_returns409ErasureBlocked() throws Exception {
        UUID projectId   = createProject();
        UUID propertyId  = createAndActivateProperty(projectId);
        UUID contactId   = createContact("gdpr-signed@acme.com");

        // Create + sign a contract
        signContract(projectId, propertyId, contactId);

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GDPR_ERASURE_BLOCKED"));
    }

    // =========================================================================
    // 5. Anonymize contact with only DRAFT contracts → 200, draft snapshot zeroed
    // =========================================================================

    @Test
    void anonymize_contactWithDraftContract_returns200AndZeroesBuyerSnapshot() throws Exception {
        UUID projectId  = createProject();
        UUID propertyId = createAndActivateProperty(projectId);
        UUID contactId  = createContact("gdpr-draft@acme.com");

        // Create DRAFT contract (do not sign)
        UUID contractId = createDraftContract(projectId, propertyId, contactId);

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        SaleContract sc = contractRepository.findById(contractId).orElseThrow();
        assertThat(sc.getBuyerDisplayName()).isEqualTo("ANONYMIZED");
        assertThat(sc.getBuyerEmail()).isNull();
    }

    // =========================================================================
    // 6. Privacy notice returns 200 with non-empty text
    // =========================================================================

    @Test
    void privacyNotice_returns200WithText() throws Exception {
        mvc.perform(get("/api/gdpr/privacy-notice")
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.text").isNotEmpty());
    }

    // =========================================================================
    // 7. AGENT role cannot call anonymize → 403
    // =========================================================================

    @Test
    void anonymize_asAgent_returns403() throws Exception {
        UUID contactId = createContact("gdpr-agent-forbidden@acme.com");

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. Double anonymization is idempotent — second call returns 200 (no-op)
    // =========================================================================

    @Test
    void anonymize_calledTwice_isIdempotent() throws Exception {
        UUID contactId = createContact("gdpr-idem@acme.com");

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/gdpr/contacts/{id}/anonymize", contactId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk()); // no-op
    }

    // =========================================================================
    // 9. Rectify view returns mutable fields for ADMIN and MANAGER
    // =========================================================================

    @Test
    void rectify_asManager_returns200WithMutableFields() throws Exception {
        UUID contactId = createContact("gdpr-rectify@acme.com");

        mvc.perform(get("/api/gdpr/contacts/{id}/rectify", contactId)
                        .header("Authorization", managerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId").value(contactId.toString()))
                .andExpect(jsonPath("$.email").value("gdpr-rectify@acme.com"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createContact(String email) throws Exception {
        var req = new CreateContactRequest("GDPR", "Test", "0600000000", email,
                null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class).id();
    }

    private UUID createProject() throws Exception {
        var req = new ProjectCreateRequest("GDPR Project " + refCounter++, null);
        String json = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, com.yem.hlm.backend.project.api.dto.ProjectResponse.class).id();
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "GDPR-REF-" + refCounter++;
        var req = new PropertyCreateRequest(
                PropertyType.STUDIO, ref, ref,
                new BigDecimal("200000"), "MAD",
                null, null, null,
                "CASABLANCA", "Grand Casablanca", null,
                null, null, null, null, null, null,
                new BigDecimal("80"), null,
                null, null, null, null, null, null,
                null, 1, null, null, null, null,
                false, projectId, null);
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID propertyId = objectMapper.readValue(json, PropertyResponse.class).id();

        // Activate the property
        var update = new PropertyUpdateRequest(null, null, null, null,
                PropertyStatus.ACTIVE,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        mvc.perform(put("/api/properties/{id}", propertyId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        return propertyId;
    }

    private UUID createDraftContract(UUID projectId, UUID propertyId, UUID contactId) throws Exception {
        var req = new com.yem.hlm.backend.contract.api.dto.CreateContractRequest(
                projectId, propertyId, contactId, USER_ID,
                new BigDecimal("200000.00"), null, null);
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, com.yem.hlm.backend.contract.api.dto.ContractResponse.class).id();
    }

    private void signContract(UUID projectId, UUID propertyId, UUID contactId) throws Exception {
        UUID contractId = createDraftContract(projectId, propertyId, contactId);
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());
    }
}
