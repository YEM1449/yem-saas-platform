package com.yem.hlm.backend.payment.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.payment.api.dto.PaymentResponse;
import com.yem.hlm.backend.payment.api.dto.RecordPaymentRequest;
import com.yem.hlm.backend.payment.domain.*;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.payment.repo.PaymentRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Records cash-in payments against payment calls.
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Call must be ISSUED or OVERDUE — not DRAFT or CLOSED.</li>
 *   <li>Payment amount may not exceed {@code call.amountDue - alreadyPaid}.</li>
 *   <li>When {@code sumPaid >= call.amountDue}: call → CLOSED, tranche → PAID.</li>
 *   <li>When {@code 0 < sumPaid < call.amountDue}: tranche → PARTIALLY_PAID.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository      paymentRepo;
    private final PaymentCallRepository  callRepo;
    private final TenantRepository       tenantRepo;
    private final CommercialAuditService auditService;

    public PaymentService(PaymentRepository paymentRepo,
                          PaymentCallRepository callRepo,
                          TenantRepository tenantRepo,
                          CommercialAuditService auditService) {
        this.paymentRepo  = paymentRepo;
        this.callRepo     = callRepo;
        this.tenantRepo   = tenantRepo;
        this.auditService = auditService;
    }

    // =========================================================================
    // Record payment
    // =========================================================================

    @Transactional
    public PaymentResponse recordPayment(UUID callId, RecordPaymentRequest req) {
        UUID tenantId = requireTenantId();
        UUID callerId = requireUserId();

        var call = callRepo.findByTenant_IdAndId(tenantId, callId)
                .orElseThrow(() -> new PaymentCallNotFoundException(callId));

        // Guard: must be open
        if (call.getStatus() == PaymentCallStatus.CLOSED) {
            throw new InvalidCallStateException("Payment call is already CLOSED: " + callId);
        }
        if (call.getStatus() == PaymentCallStatus.DRAFT) {
            throw new InvalidCallStateException(
                    "Payment call must be ISSUED or OVERDUE before recording payments; status: DRAFT");
        }

        // Guard: must not exceed remaining amount
        BigDecimal alreadyPaid = paymentRepo.sumReceivedByCall(tenantId, callId);
        BigDecimal remaining   = call.getAmountDue().subtract(alreadyPaid);
        if (req.amountReceived().compareTo(remaining) > 0) {
            throw new PaymentExceedsDueException(
                    "Payment amount " + req.amountReceived() +
                    " exceeds remaining balance " + remaining);
        }

        var tenant  = tenantRepo.getReferenceById(tenantId);
        var payment = new Payment(tenant, call,
                req.amountReceived(), req.receivedAt(),
                req.method(), req.reference(), req.notes());
        payment = paymentRepo.save(payment);

        // Recalculate total paid and update statuses
        BigDecimal totalPaid = alreadyPaid.add(req.amountReceived());
        updateStatuses(call, totalPaid);

        auditService.record(tenantId, AuditEventType.PAYMENT_RECEIVED,
                callerId, "PAYMENT_CALL", callId, null);

        return PaymentResponse.from(payment);
    }

    // =========================================================================
    // List payments for a call
    // =========================================================================

    public List<PaymentResponse> listPayments(UUID callId) {
        UUID tenantId = requireTenantId();
        // Verify the call exists in tenant (returns 404 on cross-tenant)
        callRepo.findByTenant_IdAndId(tenantId, callId)
                .orElseThrow(() -> new PaymentCallNotFoundException(callId));

        return paymentRepo.findByTenant_IdAndPaymentCall_IdOrderByReceivedAtAsc(tenantId, callId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void updateStatuses(PaymentCall call, BigDecimal totalPaid) {
        var tranche = call.getTranche();
        if (totalPaid.compareTo(call.getAmountDue()) >= 0) {
            call.setStatus(PaymentCallStatus.CLOSED);
            tranche.setStatus(TrancheStatus.PAID);
        } else {
            tranche.setStatus(TrancheStatus.PARTIALLY_PAID);
        }
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
