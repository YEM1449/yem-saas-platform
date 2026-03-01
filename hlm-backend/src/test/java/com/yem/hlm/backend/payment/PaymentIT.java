package com.yem.hlm.backend.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.payment.api.dto.CreatePaymentScheduleRequest;
import com.yem.hlm.backend.payment.api.dto.RecordPaymentRequest;
import com.yem.hlm.backend.payment.api.dto.TrancheRequest;
import com.yem.hlm.backend.payment.domain.PaymentCallStatus;
import com.yem.hlm.backend.payment.domain.PaymentMethod;
import com.yem.hlm.backend.payment.domain.TrancheStatus;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.payment.repo.PaymentRepository;
import com.yem.hlm.backend.payment.repo.PaymentScheduleRepository;
import com.yem.hlm.backend.payment.repo.PaymentTrancheRepository;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.domain.ProjectStatus;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the payment schedule / call / payment lifecycle.
 *
 * <p>Full lifecycle: create schedule → issue call → record partial payment
 * → record final payment → tranche PAID, call CLOSED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtProvider jwtProvider;

    @Autowired TenantRepository          tenantRepo;
    @Autowired UserRepository            userRepo;
    @Autowired ContactRepository         contactRepo;
    @Autowired ProjectRepository         projectRepo;
    @Autowired PropertyRepository        propertyRepo;
    @Autowired SaleContractRepository    contractRepo;
    @Autowired PaymentScheduleRepository scheduleRepo;
    @Autowired PaymentTrancheRepository  trancheRepo;
    @Autowired PaymentCallRepository     callRepo;
    @Autowired PaymentRepository         paymentRepo;

    private String adminBearer;
    private String agentBearer;
    private UUID   contractId;
    private UUID   agentId;

    @BeforeEach
    void setUp() {
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_ID, TENANT_ID, UserRole.ROLE_ADMIN);

        agentId     = UUID.randomUUID();
        agentBearer = "Bearer " + jwtProvider.generate(agentId, TENANT_ID, UserRole.ROLE_AGENT);

        Tenant tenant = tenantRepo.getReferenceById(TENANT_ID);
        User   admin  = userRepo.getReferenceById(ADMIN_ID);

        // Create agent user
        User agent = new User(tenant, "agent-pay@test.com", "{noop}pass");
        agent.setRole(UserRole.ROLE_AGENT);
        agent = userRepo.save(agent);
        // Override the agent ID to match what we put in the JWT
        agentId = agent.getId();
        agentBearer = "Bearer " + jwtProvider.generate(agentId, TENANT_ID, UserRole.ROLE_AGENT);

        // Minimal contract fixture
        var project = new Project(tenant, "Pay-IT-Project");
        projectRepo.save(project);
        var property = new Property(tenant, project, PropertyType.VILLA, UUID.randomUUID());
        property.setTitle("Villa Pay-IT");
        property.setReferenceCode("PAY-IT-" + UUID.randomUUID().toString().substring(0, 4));
        property.setStatus(PropertyStatus.SOLD);
        propertyRepo.save(property);
        var contact = new Contact(tenant, ADMIN_ID, "Pay", "Buyer");
        contactRepo.save(contact);

        var contract = new SaleContract(tenant, project, property, contact, admin);
        contract.setStatus(SaleContractStatus.SIGNED);
        contract.setAgreedPrice(new BigDecimal("1000000.00"));
        contract = contractRepo.save(contract);
        contractId = contract.getId();
    }

    // =========================================================================
    // 1. Create schedule
    // =========================================================================

    @Test
    void createSchedule_returns201() throws Exception {
        var req = scheduleRequest();

        mvc.perform(post("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tranches.length()").value(2))
                .andExpect(jsonPath("$.tranches[0].label").value("Fondations"))
                .andExpect(jsonPath("$.tranches[0].status").value("PLANNED"));
    }

    // =========================================================================
    // 2. Duplicate schedule → 409
    // =========================================================================

    @Test
    void createSchedule_duplicate_returns409() throws Exception {
        createScheduleViaApi();

        mvc.perform(post("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(scheduleRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_SCHEDULE_EXISTS"));
    }

    // =========================================================================
    // 3. Get schedule
    // =========================================================================

    @Test
    void getSchedule_returns200() throws Exception {
        createScheduleViaApi();

        mvc.perform(get("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(contractId.toString()))
                .andExpect(jsonPath("$.tranches.length()").value(2));
    }

    // =========================================================================
    // 4. Agent forbidden on write
    // =========================================================================

    @Test
    void createSchedule_agent_returns403() throws Exception {
        mvc.perform(post("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(scheduleRequest())))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. Issue call → tranche ISSUED
    // =========================================================================

    @Test
    void issueCall_trancheBecomesIssued() throws Exception {
        var scheduleJson = createScheduleViaApi();
        var trancheId = om.readTree(scheduleJson)
                .get("tranches").get(0).get("id").asText();

        mvc.perform(post("/api/contracts/{cId}/payment-schedule/tranches/{tId}/issue-call",
                        contractId, trancheId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.callNumber").value(1));

        // Verify DB: tranche is now ISSUED
        var tranche = trancheRepo.findById(UUID.fromString(trancheId)).orElseThrow();
        assertThat(tranche.getStatus()).isEqualTo(TrancheStatus.ISSUED);
    }

    // =========================================================================
    // 6. Record partial payment → tranche PARTIALLY_PAID
    // =========================================================================

    @Test
    void recordPartialPayment_tranchePartiallyPaid() throws Exception {
        var callId = createIssueAndGetCallId(0);

        var payReq = new RecordPaymentRequest(
                new BigDecimal("100000.00"),
                LocalDate.now(),
                PaymentMethod.BANK_TRANSFER,
                "VIR-001", null);

        mvc.perform(post("/api/payment-calls/{id}/payments", callId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountReceived").value(100000.0));

        var call = callRepo.findById(UUID.fromString(callId)).orElseThrow();
        assertThat(call.getTranche().getStatus()).isEqualTo(TrancheStatus.PARTIALLY_PAID);
        assertThat(call.getStatus()).isEqualTo(PaymentCallStatus.ISSUED);
    }

    // =========================================================================
    // 7. Full payment → tranche PAID, call CLOSED
    // =========================================================================

    @Test
    void recordFullPayment_tranchePaidCallClosed() throws Exception {
        var callId = createIssueAndGetCallId(0);
        var callAmountDue = new BigDecimal("300000.00"); // tranche 0 = 30%

        var payReq = new RecordPaymentRequest(
                callAmountDue,
                LocalDate.now(),
                PaymentMethod.BANK_TRANSFER,
                "VIR-FULL", null);

        mvc.perform(post("/api/payment-calls/{id}/payments", callId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payReq)))
                .andExpect(status().isCreated());

        var call = callRepo.findById(UUID.fromString(callId)).orElseThrow();
        assertThat(call.getStatus()).isEqualTo(PaymentCallStatus.CLOSED);
        assertThat(call.getTranche().getStatus()).isEqualTo(TrancheStatus.PAID);
    }

    // =========================================================================
    // 8. Payment exceeds due → 400
    // =========================================================================

    @Test
    void recordPayment_exceedsDue_returns400() throws Exception {
        var callId = createIssueAndGetCallId(0);

        var payReq = new RecordPaymentRequest(
                new BigDecimal("999999999.00"),
                LocalDate.now(),
                PaymentMethod.CASH,
                null, null);

        mvc.perform(post("/api/payment-calls/{id}/payments", callId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_DUE"));
    }

    // =========================================================================
    // 9. Unauthenticated → 401
    // =========================================================================

    @Test
    void getSchedule_noAuth_returns401() throws Exception {
        mvc.perform(get("/api/contracts/{id}/payment-schedule", contractId))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 10. Tenant isolation
    // =========================================================================

    @Test
    void getSchedule_otherTenant_returns404() throws Exception {
        createScheduleViaApi();

        UUID otherTenantId = UUID.randomUUID();
        Tenant otherTenant = tenantRepo.save(new Tenant("other-pay-" + otherTenantId, "Other"));
        User otherAdmin = new User(otherTenant, "other@pay.com", "{noop}pass");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepo.save(otherAdmin);
        String otherBearer = "Bearer " + jwtProvider.generate(
                otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", otherBearer))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String createScheduleViaApi() throws Exception {
        return mvc.perform(post("/api/contracts/{id}/payment-schedule", contractId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(scheduleRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String createIssueAndGetCallId(int trancheIndex) throws Exception {
        var scheduleJson = createScheduleViaApi();
        var trancheId = om.readTree(scheduleJson)
                .get("tranches").get(trancheIndex).get("id").asText();

        return om.readTree(
                mvc.perform(post("/api/contracts/{cId}/payment-schedule/tranches/{tId}/issue-call",
                                contractId, trancheId)
                                .header("Authorization", adminBearer))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asText();
    }

    private CreatePaymentScheduleRequest scheduleRequest() {
        return new CreatePaymentScheduleRequest(
                List.of(
                        new TrancheRequest("Fondations", new BigDecimal("30.00"),
                                new BigDecimal("300000.00"),
                                LocalDate.now().plusMonths(3), null),
                        new TrancheRequest("Livraison", new BigDecimal("70.00"),
                                new BigDecimal("700000.00"),
                                LocalDate.now().plusMonths(12), null)
                ),
                "Test schedule notes"
        );
    }
}
