package com.yem.hlm.backend.payment.service;

import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.payment.api.dto.*;
import com.yem.hlm.backend.payment.domain.*;
import com.yem.hlm.backend.payment.repo.PaymentScheduleRepository;
import com.yem.hlm.backend.payment.repo.PaymentTrancheRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages payment schedule lifecycle for sale contracts.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>One schedule per contract — duplicate → {@link PaymentScheduleAlreadyExistsException}.</li>
 *   <li>Tranche percentages must sum to 100.00 (±0.01 tolerance).</li>
 *   <li>Tranche amounts must sum to contract.agreedPrice (±1 MAD tolerance).</li>
 *   <li>Tranche updates limited to PLANNED status.</li>
 * </ul>
 *
 * <h3>RBAC</h3>
 * Create/update → enforced at controller level (ADMIN/MANAGER only).
 * Read → all roles; AGENT scoped to own contracts.
 */
@Service
@Transactional(readOnly = true)
public class PaymentScheduleService {

    private static final BigDecimal PERCENTAGE_TOLERANCE = new BigDecimal("0.05");
    private static final BigDecimal AMOUNT_TOLERANCE     = new BigDecimal("1.00");
    private static final BigDecimal HUNDRED              = new BigDecimal("100");

    private final PaymentScheduleRepository scheduleRepo;
    private final PaymentTrancheRepository  trancheRepo;
    private final SaleContractRepository    contractRepo;
    private final TenantRepository          tenantRepo;

    public PaymentScheduleService(PaymentScheduleRepository scheduleRepo,
                                  PaymentTrancheRepository trancheRepo,
                                  SaleContractRepository contractRepo,
                                  TenantRepository tenantRepo) {
        this.scheduleRepo = scheduleRepo;
        this.trancheRepo  = trancheRepo;
        this.contractRepo = contractRepo;
        this.tenantRepo   = tenantRepo;
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Transactional
    public PaymentScheduleResponse createSchedule(UUID contractId,
                                                  CreatePaymentScheduleRequest req) {
        UUID tenantId = requireTenantId();

        // Guard: contract must exist in tenant
        var contract = contractRepo.findByTenant_IdAndId(tenantId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        // Guard: one schedule per contract
        if (scheduleRepo.existsByTenant_IdAndSaleContract_Id(tenantId, contractId)) {
            throw new PaymentScheduleAlreadyExistsException(contractId);
        }

        // Validate tranche sums
        validateTrancheSums(req.tranches(), contract.getAgreedPrice());

        var tenant   = tenantRepo.getReferenceById(tenantId);
        var schedule = new PaymentSchedule(tenant, contract, req.notes());
        schedule = scheduleRepo.save(schedule);

        // Build tranches
        var tranches = new ArrayList<PaymentTranche>();
        int order = 1;
        for (var t : req.tranches()) {
            var tranche = new PaymentTranche(
                    tenant, schedule,
                    order++,
                    t.label(), t.percentage(), t.amount(),
                    t.dueDate(), t.triggerCondition());
            tranches.add(trancheRepo.save(tranche));
        }

        // Reload with tranches for response
        var saved = scheduleRepo.findWithTranches(tenantId, contractId).orElseThrow();
        return PaymentScheduleResponse.from(saved);
    }

    // =========================================================================
    // Read
    // =========================================================================

    public PaymentScheduleResponse getByContractId(UUID contractId) {
        UUID tenantId = requireTenantId();

        // AGENT: only own contracts — guard via contract repo
        if (callerIsAgent()) {
            UUID callerId = requireUserId();
            contractRepo.findByTenant_IdAndId(tenantId, contractId)
                    .filter(c -> c.getAgent().getId().equals(callerId))
                    .orElseThrow(() -> new ContractNotFoundException(contractId));
        }

        return scheduleRepo.findWithTranches(tenantId, contractId)
                .map(PaymentScheduleResponse::from)
                .orElseThrow(() -> new ContractNotFoundException(contractId));
    }

    // =========================================================================
    // Update tranche (PLANNED only)
    // =========================================================================

    @Transactional
    public TrancheResponse updateTranche(UUID trancheId, UpdateTrancheRequest req) {
        UUID tenantId = requireTenantId();
        var tranche = trancheRepo.findByTenant_IdAndId(tenantId, trancheId)
                .orElseThrow(() -> new TrancheNotFoundException(trancheId));

        if (tranche.getStatus() != TrancheStatus.PLANNED) {
            throw new InvalidCallStateException(
                    "Only PLANNED tranches can be updated; current status: " + tranche.getStatus());
        }

        if (req.label() != null)            tranche.setLabel(req.label());
        if (req.amount() != null)           tranche.setAmount(req.amount());
        if (req.dueDate() != null)          tranche.setDueDate(req.dueDate());
        if (req.triggerCondition() != null) tranche.setTriggerCondition(req.triggerCondition());

        return TrancheResponse.from(tranche);
    }

    // =========================================================================
    // Validation helpers
    // =========================================================================

    private void validateTrancheSums(List<TrancheRequest> tranches, BigDecimal agreedPrice) {
        BigDecimal totalPct = tranches.stream()
                .map(TrancheRequest::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (HUNDRED.subtract(totalPct).abs().compareTo(PERCENTAGE_TOLERANCE) > 0) {
            throw new InvalidTrancheSumException(
                    "Tranche percentages must sum to 100; got " +
                    totalPct.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal totalAmt = tranches.stream()
                .map(TrancheRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (agreedPrice.subtract(totalAmt).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            throw new InvalidTrancheSumException(
                    "Tranche amounts must sum to the contract agreed price (" +
                    agreedPrice.toPlainString() + "); got " +
                    totalAmt.setScale(2, RoundingMode.HALF_UP));
        }
    }

    // =========================================================================
    // Security helpers
    // =========================================================================

    private UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new IllegalStateException("Missing tenant context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = TenantContext.getUserId();
        if (id == null) throw new IllegalStateException("Missing user context");
        return id;
    }

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }
}
