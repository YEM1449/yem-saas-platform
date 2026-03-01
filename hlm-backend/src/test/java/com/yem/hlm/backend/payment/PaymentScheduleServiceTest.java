package com.yem.hlm.backend.payment;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.payment.api.dto.CreatePaymentScheduleRequest;
import com.yem.hlm.backend.payment.api.dto.TrancheRequest;
import com.yem.hlm.backend.payment.domain.PaymentSchedule;
import com.yem.hlm.backend.payment.repo.PaymentScheduleRepository;
import com.yem.hlm.backend.payment.repo.PaymentTrancheRepository;
import com.yem.hlm.backend.payment.service.InvalidTrancheSumException;
import com.yem.hlm.backend.payment.service.PaymentScheduleAlreadyExistsException;
import com.yem.hlm.backend.payment.service.PaymentScheduleService;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentScheduleService focusing on validation rules.
 */
@ExtendWith(MockitoExtension.class)
class PaymentScheduleServiceTest {

    @Mock private PaymentScheduleRepository scheduleRepo;
    @Mock private PaymentTrancheRepository  trancheRepo;
    @Mock private SaleContractRepository    contractRepo;
    @Mock private TenantRepository          tenantRepo;

    @InjectMocks
    private PaymentScheduleService service;

    private UUID       tenantId;
    private UUID       contractId;
    private SaleContract contract;

    @BeforeEach
    void setUp() {
        tenantId   = UUID.randomUUID();
        contractId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(UUID.randomUUID());

        // Set up ADMIN security context
        var auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        contract = mock(SaleContract.class);
        when(contract.getId()).thenReturn(contractId);
        when(contract.getAgreedPrice()).thenReturn(new BigDecimal("1000000.00"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. contract not found → ContractNotFoundException
    // =========================================================================

    @Test
    void createSchedule_contractNotFound_throws() {
        when(contractRepo.findByTenant_IdAndId(tenantId, contractId))
                .thenReturn(Optional.empty());

        var req = new CreatePaymentScheduleRequest(
                List.of(tranche("T1", 100, 1_000_000)), null);

        assertThatThrownBy(() -> service.createSchedule(contractId, req))
                .isInstanceOf(ContractNotFoundException.class);
    }

    // =========================================================================
    // 2. schedule already exists → PaymentScheduleAlreadyExistsException
    // =========================================================================

    @Test
    void createSchedule_alreadyExists_throws() {
        when(contractRepo.findByTenant_IdAndId(tenantId, contractId))
                .thenReturn(Optional.of(contract));
        when(scheduleRepo.existsByTenant_IdAndSaleContract_Id(tenantId, contractId))
                .thenReturn(true);

        var req = new CreatePaymentScheduleRequest(
                List.of(tranche("T1", 100, 1_000_000)), null);

        assertThatThrownBy(() -> service.createSchedule(contractId, req))
                .isInstanceOf(PaymentScheduleAlreadyExistsException.class);
    }

    // =========================================================================
    // 3. percentage sum != 100 → InvalidTrancheSumException
    // =========================================================================

    @Test
    void createSchedule_badPercentageSum_throws() {
        when(contractRepo.findByTenant_IdAndId(tenantId, contractId))
                .thenReturn(Optional.of(contract));
        when(scheduleRepo.existsByTenant_IdAndSaleContract_Id(tenantId, contractId))
                .thenReturn(false);

        // 30 + 30 = 60, not 100
        var req = new CreatePaymentScheduleRequest(
                List.of(
                        tranche("T1", 30, 300_000),
                        tranche("T2", 30, 300_000)
                ), null);

        assertThatThrownBy(() -> service.createSchedule(contractId, req))
                .isInstanceOf(InvalidTrancheSumException.class)
                .hasMessageContaining("100");
    }

    // =========================================================================
    // 4. amount sum != agreedPrice → InvalidTrancheSumException
    // =========================================================================

    @Test
    void createSchedule_badAmountSum_throws() {
        when(contractRepo.findByTenant_IdAndId(tenantId, contractId))
                .thenReturn(Optional.of(contract));
        when(scheduleRepo.existsByTenant_IdAndSaleContract_Id(tenantId, contractId))
                .thenReturn(false);

        // percentages sum to 100 but amounts sum to 900_000, not 1_000_000
        var req = new CreatePaymentScheduleRequest(
                List.of(
                        tranche("T1", 50, 400_000),
                        tranche("T2", 50, 500_000)
                ), null);

        assertThatThrownBy(() -> service.createSchedule(contractId, req))
                .isInstanceOf(InvalidTrancheSumException.class)
                .hasMessageContaining("agreed price");
    }

    // =========================================================================
    // 5. valid schedule → saved and returned
    // =========================================================================

    @Test
    void createSchedule_valid_callsSaveAndReturns() {
        when(contractRepo.findByTenant_IdAndId(tenantId, contractId))
                .thenReturn(Optional.of(contract));
        when(scheduleRepo.existsByTenant_IdAndSaleContract_Id(tenantId, contractId))
                .thenReturn(false);

        var schedule = mock(PaymentSchedule.class);
        when(schedule.getId()).thenReturn(UUID.randomUUID());
        when(schedule.getSaleContract()).thenReturn(contract);
        when(schedule.getNotes()).thenReturn(null);
        when(schedule.getCreatedAt()).thenReturn(null);
        when(schedule.getTranches()).thenReturn(List.of());

        when(tenantRepo.getReferenceById(any())).thenReturn(mock(Tenant.class));
        when(scheduleRepo.save(any())).thenReturn(schedule);
        when(scheduleRepo.findWithTranches(tenantId, contractId))
                .thenReturn(Optional.of(schedule));

        var req = new CreatePaymentScheduleRequest(
                List.of(
                        tranche("Fondations", 30, 300_000),
                        tranche("Structure",  70, 700_000)
                ), "Notes test");

        service.createSchedule(contractId, req);

        verify(scheduleRepo, times(1)).save(any());
        verify(trancheRepo, times(2)).save(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TrancheRequest tranche(String label, double pct, double amount) {
        return new TrancheRequest(
                label,
                BigDecimal.valueOf(pct),
                BigDecimal.valueOf(amount),
                null, null);
    }
}
