package com.yem.hlm.backend.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.payments.api.dto.AddPaymentRequest;
import com.yem.hlm.backend.payments.api.dto.CreateScheduleItemRequest;
import com.yem.hlm.backend.payments.api.dto.PaymentScheduleItemResponse;
import com.yem.hlm.backend.payments.api.dto.SendScheduleItemRequest;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Payment Schedule feature (appels de fonds).
 *
 * <p>Tests cover:
 * <ol>
 *   <li>Create + list schedule items</li>
 *   <li>DRAFT → ISSUED lifecycle</li>
 *   <li>Partial payment + remaining computation</li>
 *   <li>Full payment → auto-transition to PAID</li>
 *   <li>Cannot issue CANCELED item (409)</li>
 *   <li>AGENT read-only (403 on write)</li>
 *   <li>Tenant isolation (404 cross-tenant)</li>
 *   <li>PDF download (200 + application/pdf)</li>
 *   <li>Cancel item</li>
 *   <li>Send (ISSUED → SENT + outbox queued)</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentScheduleIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider  jwtProvider;

    private String adminBearer;
    private String agentBearer;
    private int    counter = 0;

    @BeforeEach
    void setup() {
        adminBearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
        agentBearer = "Bearer " + jwtProvider.generate(
                USER_ID, TENANT_ID, UserRole.ROLE_AGENT);
    }

    // =========================================================================
    // 1. create_and_list_scheduleItems
    // =========================================================================

    @Test
    void create_and_list_scheduleItems() throws Exception {
        UUID contractId = newSignedContract();

        var req = new CreateScheduleItemRequest("Acompte 1 – 20%",
                new BigDecimal("100000.00"), LocalDate.now().plusDays(30), "Test note");

        String json = mvc.perform(post("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Acompte 1 – 20%"))
                .andExpect(jsonPath("$.status").value(PaymentScheduleStatus.DRAFT.name()))
                .andExpect(jsonPath("$.amountPaid").value(0))
                .andExpect(jsonPath("$.amountRemaining").value(100000.00))
                .andReturn().getResponse().getContentAsString();

        UUID itemId = UUID.fromString(objectMapper.readTree(json).get("id").asText());

        // List returns the created item
        mvc.perform(get("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(itemId.toString()));
    }

    // =========================================================================
    // 2. issue_transitions_DRAFT_to_ISSUED
    // =========================================================================

    @Test
    void issue_transitions_DRAFT_to_ISSUED() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("50000.00"), 30);

        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty());
    }

    // =========================================================================
    // 3. partialPayment_updates_remainingAmount
    // =========================================================================

    @Test
    void partialPayment_updates_remainingAmount() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("200000.00"), 60);

        // Issue first
        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Partial payment: 80000 / 200000
        var payReq = new AddPaymentRequest(new BigDecimal("80000.00"),
                LocalDate.now(), "BANK_TRANSFER", "REF001", null);

        mvc.perform(post("/api/schedule-items/{id}/payments", itemId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPaid").value(80000.00));

        // Schedule item still ISSUED with correct remaining
        mvc.perform(get("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(jsonPath("$[0].status").value("ISSUED"))
                .andExpect(jsonPath("$[0].amountPaid").value(80000.00))
                .andExpect(jsonPath("$[0].amountRemaining").value(120000.00));
    }

    // =========================================================================
    // 4. fullPayment_autoTransitions_to_PAID
    // =========================================================================

    @Test
    void fullPayment_autoTransitions_to_PAID() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("50000.00"), 60);

        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Pay exact amount → PAID
        var payReq = new AddPaymentRequest(new BigDecimal("50000.00"),
                LocalDate.now(), null, null, null);

        mvc.perform(post("/api/schedule-items/{id}/payments", itemId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].amountRemaining").value(0));
    }

    // =========================================================================
    // 5. issue_already_CANCELED_returns_409
    // =========================================================================

    @Test
    void issue_already_CANCELED_returns_409() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("30000.00"), 30);

        // Cancel first
        mvc.perform(post("/api/schedule-items/{id}/cancel", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        // Issue on CANCELED → 409
        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SCHEDULE_STATE"));
    }

    // =========================================================================
    // 6. agent_cannotWrite_returnsFor bidden
    // =========================================================================

    @Test
    void agent_cannotCreate_returnsForbidden() throws Exception {
        UUID contractId = newSignedContract();

        var req = new CreateScheduleItemRequest("Test", new BigDecimal("10000.00"),
                LocalDate.now().plusDays(10), null);

        mvc.perform(post("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. agent_canRead_schedule
    // =========================================================================

    @Test
    void agent_canRead_schedule() throws Exception {
        UUID contractId = newSignedContract();
        createItem(contractId, new BigDecimal("25000.00"), 45);

        mvc.perform(get("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // =========================================================================
    // 8. pdf_download_returnsPdfBytes
    // =========================================================================

    @Test
    void pdf_download_returnsPdfBytes() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("75000.00"), 90);

        byte[] pdf = mvc.perform(get("/api/schedule-items/{id}/pdf", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        // Verify it starts with %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    // =========================================================================
    // 9. tenantIsolation_crossTenantAccess_returns404
    // =========================================================================

    @Test
    void tenantIsolation_crossTenantAccess_returns404() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("40000.00"), 30);

        // Bearer from a different tenant — use a real userId so the security filter accepts the token
        UUID otherTenantId = UUID.randomUUID();
        String otherBearer = "Bearer " + jwtProvider.generate(
                USER_ID, otherTenantId, UserRole.ROLE_ADMIN);

        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", otherBearer))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 10. send_issues_SENT_transition
    // =========================================================================

    @Test
    void send_transitions_ISSUED_to_SENT() throws Exception {
        UUID contractId = newSignedContract();
        UUID itemId     = createItem(contractId, new BigDecimal("60000.00"), 45);

        // Issue first
        mvc.perform(post("/api/schedule-items/{id}/issue", itemId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Send with emailOverride (buyer email set in contract)
        var sendReq = new SendScheduleItemRequest(null, "test@example.com", null, true, false);

        mvc.perform(post("/api/schedule-items/{id}/send", itemId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a SIGNED contract (project + active property + contact + draft + sign). */
    private UUID newSignedContract() throws Exception {
        UUID projectId  = createProject();
        UUID propertyId = createAndActivateProperty(projectId);
        ContactResponse buyer = createContact("pay-it-" + (++counter) + "@acme.com");

        var req = new CreateContractRequest(
                projectId, propertyId, buyer.id(),
                USER_ID, new BigDecimal("500000.00"), null, null
        );
        String json = mvc.perform(post("/api/contracts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID contractId = objectMapper.readValue(json, ContractResponse.class).id();

        // Sign so property is SOLD and contract is SIGNED
        mvc.perform(post("/api/contracts/{id}/sign", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        return contractId;
    }

    private UUID createProject() throws Exception {
        String name = "PAY-PROJ-" + (++counter);
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAndActivateProperty(UUID projectId) throws Exception {
        String ref = "PAY-PROP-" + (++counter);
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Pay Test Apt " + ref, ref,
                new BigDecimal("500000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("80"), new BigDecimal("100"),
                2, 1, 0, null, null, null, null, null, null, null, null, null,
                null, projectId, null
        );
        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse prop = objectMapper.readValue(json, PropertyResponse.class);

        var update = new PropertyUpdateRequest(
                null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null
        );
        mvc.perform(put("/api/properties/{id}", prop.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        return prop.id();
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Marie", "Martin", null, email, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }

    private UUID createItem(UUID contractId, BigDecimal amount, int dueDays) throws Exception {
        var req = new CreateScheduleItemRequest(
                "Échéance " + (++counter),
                amount,
                LocalDate.now().plusDays(dueDays),
                null
        );
        String json = mvc.perform(post("/api/contracts/{id}/schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
