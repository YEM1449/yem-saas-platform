package com.yem.hlm.backend.payment.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.payment.api.dto.PaymentCallResponse;
import com.yem.hlm.backend.payment.domain.*;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.payment.repo.PaymentTrancheRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages the PaymentCall (Appel de Fonds) lifecycle.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 * DRAFT → ISSUED (issueCall)
 * ISSUED → OVERDUE (markOverdueCalls — scheduler)
 * ISSUED | OVERDUE → CLOSED (via PaymentService when fully paid)
 * </pre>
 *
 * <h3>Side effects on tranche</h3>
 * {@code issueCall()} moves the tranche from PLANNED → ISSUED.
 */
@Service
@Transactional(readOnly = true)
public class PaymentCallService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallService.class);

    private final PaymentCallRepository    callRepo;
    private final PaymentTrancheRepository trancheRepo;
    private final TenantRepository         tenantRepo;
    private final CommercialAuditService   auditService;

    public PaymentCallService(PaymentCallRepository callRepo,
                              PaymentTrancheRepository trancheRepo,
                              TenantRepository tenantRepo,
                              CommercialAuditService auditService) {
        this.callRepo     = callRepo;
        this.trancheRepo  = trancheRepo;
        this.tenantRepo   = tenantRepo;
        this.auditService = auditService;
    }

    // =========================================================================
    // Issue call (DRAFT → ISSUED)
    // =========================================================================

    @Transactional
    public PaymentCallResponse issueCall(UUID trancheId) {
        UUID tenantId = requireTenantId();
        UUID callerId = requireUserId();

        var tranche = trancheRepo.findByTenant_IdAndId(tenantId, trancheId)
                .orElseThrow(() -> new TrancheNotFoundException(trancheId));

        // Guard: tranche must be PLANNED to issue a call
        if (tranche.getStatus() != TrancheStatus.PLANNED) {
            throw new InvalidCallStateException(
                    "Cannot issue a call for tranche with status: " + tranche.getStatus());
        }

        // Move tranche → ISSUED
        tranche.setStatus(TrancheStatus.ISSUED);

        // Determine call number (usually 1; could be more if re-issuing after edge cases)
        int callNumber = callRepo.findByTenant_IdAndTranche_IdOrderByCallNumberAsc(
                tenantId, trancheId).size() + 1;

        var tenant = tenantRepo.getReferenceById(tenantId);
        var call = new PaymentCall(tenant, tranche, callNumber,
                tranche.getAmount(), tranche.getDueDate());
        call.setIssuedAt(LocalDateTime.now());
        call.setStatus(PaymentCallStatus.ISSUED);
        call = callRepo.save(call);

        auditService.record(tenantId, AuditEventType.PAYMENT_CALL_ISSUED,
                callerId, "PAYMENT_CALL", call.getId(), null);

        return PaymentCallResponse.from(call);
    }

    // =========================================================================
    // List calls (tenant-scoped, paged)
    // =========================================================================

    public Page<PaymentCallResponse> listCalls(Pageable pageable) {
        UUID tenantId = requireTenantId();
        return callRepo.findByTenant_Id(tenantId, pageable)
                .map(PaymentCallResponse::from);
    }

    // =========================================================================
    // Get single call
    // =========================================================================

    public PaymentCallResponse getCall(UUID callId) {
        UUID tenantId = requireTenantId();
        var call = callRepo.findByTenant_IdAndId(tenantId, callId)
                .orElseThrow(() -> new PaymentCallNotFoundException(callId));
        enforceAgentOwnership(callId, call);
        return PaymentCallResponse.from(call);
    }

    // =========================================================================
    // Overdue scheduler step
    // =========================================================================

    /**
     * Marks ISSUED calls whose due_date is in the past as OVERDUE.
     * Also marks the parent tranche OVERDUE.
     * Called by {@link com.yem.hlm.backend.payment.service.PaymentOverdueScheduler}.
     */
    @Transactional
    public void markOverdueCalls(UUID tenantId) {
        LocalDate today = LocalDate.now();
        List<PaymentCall> overdue = callRepo.findOverdueCalls(tenantId, today);
        for (PaymentCall pc : overdue) {
            pc.setStatus(PaymentCallStatus.OVERDUE);
            if (pc.getTranche().getStatus() == TrancheStatus.ISSUED ||
                    pc.getTranche().getStatus() == TrancheStatus.PARTIALLY_PAID) {
                pc.getTranche().setStatus(TrancheStatus.OVERDUE);
            }
            log.info("[OVERDUE] paymentCall={} tranche={}", pc.getId(), pc.getTranche().getId());
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    PaymentCall requireCall(UUID tenantId, UUID callId) {
        return callRepo.findByTenant_IdAndId(tenantId, callId)
                .orElseThrow(() -> new PaymentCallNotFoundException(callId));
    }

    private void enforceAgentOwnership(UUID callId, PaymentCall call) {
        if (!callerIsAgent()) return;
        UUID callerId = requireUserId();
        UUID contractAgent = call.getTranche().getSchedule().getSaleContract().getAgent().getId();
        if (!callerId.equals(contractAgent)) {
            throw new PaymentCallNotFoundException(callId); // 404 to avoid info leak
        }
    }

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }

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
}
