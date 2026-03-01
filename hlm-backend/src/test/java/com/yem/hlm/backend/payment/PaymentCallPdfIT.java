package com.yem.hlm.backend.payment;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.payment.domain.PaymentCall;
import com.yem.hlm.backend.payment.domain.PaymentCallStatus;
import com.yem.hlm.backend.payment.domain.PaymentSchedule;
import com.yem.hlm.backend.payment.domain.PaymentTranche;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.payment.repo.PaymentScheduleRepository;
import com.yem.hlm.backend.payment.repo.PaymentTrancheRepository;
import com.yem.hlm.backend.project.domain.Project;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf
 *
 * <p>Covers:
 * <ul>
 *   <li>200 OK + {@code application/pdf} content-type</li>
 *   <li>{@code %PDF} magic bytes in response body</li>
 *   <li>404 for non-existent payment call</li>
 *   <li>404 for cross-tenant access (tenant isolation)</li>
 *   <li>RBAC: AGENT can download PDF for their own contract</li>
 *   <li>RBAC: AGENT denied (404) for a different agent's contract</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentCallPdfIT extends IntegrationTestBase {

    private static final String PDF_ENDPOINT = "/api/payment-calls/{id}/documents/appel-de-fonds.pdf";

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository        tenantRepo;
    @Autowired UserRepository          userRepo;
    @Autowired ContactRepository       contactRepo;
    @Autowired ProjectRepository       projectRepo;
    @Autowired PropertyRepository      propertyRepo;
    @Autowired SaleContractRepository  contractRepo;
    @Autowired PaymentScheduleRepository scheduleRepo;
    @Autowired PaymentTrancheRepository  trancheRepo;
    @Autowired PaymentCallRepository     callRepo;

    private String adminBearer;

    @BeforeEach
    void setupToken() {
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    // ===== Happy path (ADMIN) =====

    @Test
    void downloadPdf_asAdmin_returns200WithPdfContentType() throws Exception {
        UUID callId = createIssuedCall(ADMIN_ID);

        mvc.perform(get(PDF_ENDPOINT, callId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_asAdmin_bodyStartsWithPdfMagicBytes() throws Exception {
        UUID callId = createIssuedCall(ADMIN_ID);

        byte[] body = mvc.perform(get(PDF_ENDPOINT, callId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSizeGreaterThan(500);
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    // ===== Not found =====

    @Test
    void downloadPdf_nonExistentCall_returns404() throws Exception {
        mvc.perform(get(PDF_ENDPOINT, UUID.randomUUID())
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // ===== Tenant isolation =====

    @Test
    void downloadPdf_crossTenant_returns404() throws Exception {
        UUID callId  = createIssuedCall(ADMIN_ID);
        String bearerB = createOtherTenantBearer("pdf-adf-iso");

        mvc.perform(get(PDF_ENDPOINT, callId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== RBAC — AGENT =====

    @Test
    void downloadPdf_asAgent_ownContract_returns200() throws Exception {
        User agent = userRepo.save(new User(
                tenantRepo.getReferenceById(TENANT_ID),
                "agent-pdf-own@acme.com", "{noop}pass", UserRole.ROLE_AGENT));
        String agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        UUID callId = createIssuedCall(agent.getId());

        mvc.perform(get(PDF_ENDPOINT, callId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadPdf_asAgent_otherContract_returns404() throws Exception {
        // Admin's call (contract agent = ADMIN_ID)
        UUID callId = createIssuedCall(ADMIN_ID);

        // A different agent who is NOT the contract agent
        User agent = userRepo.save(new User(
                tenantRepo.getReferenceById(TENANT_ID),
                "agent-pdf-other@acme.com", "{noop}pass", UserRole.ROLE_AGENT));
        String agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), TENANT_ID, UserRole.ROLE_AGENT);

        mvc.perform(get(PDF_ENDPOINT, callId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isNotFound());
    }

    // ===== Helpers =====

    /**
     * Builds a minimal DB-direct fixture with the given user as the contract's agent,
     * creates a payment schedule + one tranche + one ISSUED call, and returns the call ID.
     */
    private UUID createIssuedCall(UUID agentUserId) {
        Tenant tenant = tenantRepo.getReferenceById(TENANT_ID);
        User   agent  = userRepo.getReferenceById(agentUserId);

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        var project  = projectRepo.save(new Project(tenant, "PdfIT-" + suffix));
        var property = new Property(tenant, project, PropertyType.VILLA, UUID.randomUUID());
        property.setTitle("Villa ADF " + suffix);
        property.setReferenceCode("ADF-" + suffix);
        property.setStatus(PropertyStatus.SOLD);
        propertyRepo.save(property);

        var contact  = contactRepo.save(new Contact(tenant, agentUserId, "Adf", "Buyer"));

        var contract = new SaleContract(tenant, project, property, contact, agent);
        contract.setStatus(SaleContractStatus.SIGNED);
        contract.setAgreedPrice(new BigDecimal("1000000.00"));
        contractRepo.save(contract);

        var schedule = scheduleRepo.save(new PaymentSchedule(tenant, contract, null));
        var tranche  = trancheRepo.save(new PaymentTranche(
                tenant, schedule, 1, "Fondations ADF",
                new BigDecimal("30.00"), new BigDecimal("300000.00"),
                LocalDate.now().plusMonths(3), null));

        var call = new PaymentCall(tenant, tranche, 1,
                new BigDecimal("300000.00"), LocalDate.now().plusMonths(3));
        call.setStatus(PaymentCallStatus.ISSUED);
        call.setIssuedAt(LocalDateTime.now());
        return callRepo.save(call).getId();
    }

    private String createOtherTenantBearer(String keyPrefix) {
        Tenant tenantB = tenantRepo.save(
                new Tenant(keyPrefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                           "PDF Isolation Tenant"));
        User userB = userRepo.save(
                new User(tenantB, "admin@" + keyPrefix + ".com", "{noop}pass", UserRole.ROLE_ADMIN));
        return "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);
    }
}
