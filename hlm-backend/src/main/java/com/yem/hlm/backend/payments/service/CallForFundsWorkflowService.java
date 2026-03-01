package com.yem.hlm.backend.payments.service;

import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.outbox.api.dto.SendMessageRequest;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.service.MessageComposeService;
import com.yem.hlm.backend.payments.api.dto.AddPaymentRequest;
import com.yem.hlm.backend.payments.api.dto.PaymentResponse;
import com.yem.hlm.backend.payments.api.dto.PaymentScheduleItemResponse;
import com.yem.hlm.backend.payments.api.dto.SendScheduleItemRequest;
import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import com.yem.hlm.backend.payments.domain.SchedulePayment;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow transitions for payment schedule items:
 * <ul>
 *   <li>issue   — DRAFT → ISSUED</li>
 *   <li>send    — ISSUED → SENT (or re-send for SENT/OVERDUE); queues outbox messages</li>
 *   <li>cancel  — any non-PAID → CANCELED</li>
 *   <li>addPayment — records a (partial) payment; marks PAID when remaining ≤ 0</li>
 * </ul>
 *
 * <p>RBAC: ADMIN/MANAGER only for write operations (enforced at controller layer via @PreAuthorize).
 */
@Service
public class CallForFundsWorkflowService {

    private static final Set<PaymentScheduleStatus> SENDABLE = Set.of(
            PaymentScheduleStatus.ISSUED, PaymentScheduleStatus.SENT, PaymentScheduleStatus.OVERDUE);

    private final PaymentScheduleItemRepository itemRepo;
    private final SchedulePaymentRepository     paymentRepo;
    private final TenantRepository              tenantRepo;
    private final MessageComposeService         messageService;
    private final PaymentScheduleService        scheduleService; // for toResponse helper

    public CallForFundsWorkflowService(PaymentScheduleItemRepository itemRepo,
                                       SchedulePaymentRepository paymentRepo,
                                       TenantRepository tenantRepo,
                                       MessageComposeService messageService,
                                       PaymentScheduleService scheduleService) {
        this.itemRepo       = itemRepo;
        this.paymentRepo    = paymentRepo;
        this.tenantRepo     = tenantRepo;
        this.messageService = messageService;
        this.scheduleService = scheduleService;
    }

    // ── issue ────────────────────────────────────────────────────────────────

    /**
     * Transitions item from DRAFT → ISSUED.
     * Sets {@code issuedAt} timestamp.
     */
    @Transactional
    public PaymentScheduleItemResponse issue(UUID itemId) {
        UUID tenantId = requireTenantId();
        PaymentScheduleItem item = scheduleService.requireItem(tenantId, itemId);

        if (item.getStatus() != PaymentScheduleStatus.DRAFT) {
            throw new InvalidPaymentScheduleStateException(
                    "Only DRAFT items can be issued; current status: " + item.getStatus());
        }
        item.setStatus(PaymentScheduleStatus.ISSUED);
        item.setIssuedAt(LocalDateTime.now());
        return scheduleService.toResponse(itemRepo.save(item), tenantId);
    }

    // ── send ─────────────────────────────────────────────────────────────────

    /**
     * Sends call-for-funds notifications via outbox (email and/or SMS).
     * Transitions ISSUED → SENT; SENT/OVERDUE items are re-notified without status change.
     */
    @Transactional
    public PaymentScheduleItemResponse send(UUID itemId, SendScheduleItemRequest req) {
        UUID tenantId = requireTenantId();
        PaymentScheduleItem item = scheduleService.requireItem(tenantId, itemId);

        if (!SENDABLE.contains(item.getStatus())) {
            throw new InvalidPaymentScheduleStateException(
                    "Item must be ISSUED, SENT, or OVERDUE to send; current status: " + item.getStatus());
        }

        String body = buildSendBody(item);
        String subject = "Appel de fonds – " + item.getLabel();

        if (req.sendEmail()) {
            String recipient = req.emailOverride();
            messageService.compose(new SendMessageRequest(
                    MessageChannel.EMAIL,
                    (recipient == null || recipient.isBlank()) ? req.contactId() : null,
                    (recipient != null && !recipient.isBlank()) ? recipient : null,
                    subject,
                    body,
                    "PAYMENT_SCHEDULE_ITEM",
                    itemId
            ));
        }

        if (req.sendSms()) {
            String smsText = "Appel de fonds: " + item.getLabel()
                    + " – Montant: " + item.getAmount().toPlainString()
                    + " – Échéance: " + item.getDueDate();
            String recipient = req.smsOverride();
            messageService.compose(new SendMessageRequest(
                    MessageChannel.SMS,
                    (recipient == null || recipient.isBlank()) ? req.contactId() : null,
                    (recipient != null && !recipient.isBlank()) ? recipient : null,
                    null,
                    smsText,
                    "PAYMENT_SCHEDULE_ITEM",
                    itemId
            ));
        }

        // ISSUED → SENT; already SENT/OVERDUE — keep as-is
        if (item.getStatus() == PaymentScheduleStatus.ISSUED) {
            item.setStatus(PaymentScheduleStatus.SENT);
            item.setSentAt(LocalDateTime.now());
        }

        return scheduleService.toResponse(itemRepo.save(item), tenantId);
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    /**
     * Cancels the schedule item (any state except PAID).
     */
    @Transactional
    public PaymentScheduleItemResponse cancel(UUID itemId) {
        UUID tenantId = requireTenantId();
        PaymentScheduleItem item = scheduleService.requireItem(tenantId, itemId);

        if (item.getStatus() == PaymentScheduleStatus.PAID) {
            throw new InvalidPaymentScheduleStateException("Cannot cancel a fully PAID item");
        }
        if (item.getStatus() == PaymentScheduleStatus.CANCELED) {
            throw new InvalidPaymentScheduleStateException("Item is already CANCELED");
        }
        item.setStatus(PaymentScheduleStatus.CANCELED);
        item.setCanceledAt(LocalDateTime.now());
        return scheduleService.toResponse(itemRepo.save(item), tenantId);
    }

    // ── addPayment ───────────────────────────────────────────────────────────

    /**
     * Records a (partial) payment against a schedule item.
     * Automatically transitions to PAID when the total collected ≥ item amount.
     *
     * @return the new payment record
     */
    @Transactional
    public PaymentResponse addPayment(UUID itemId, AddPaymentRequest req) {
        UUID tenantId = requireTenantId();
        UUID actorId  = scheduleService.requireUserId();
        PaymentScheduleItem item = scheduleService.requireItem(tenantId, itemId);

        if (item.getStatus() == PaymentScheduleStatus.CANCELED) {
            throw new InvalidPaymentScheduleStateException("Cannot add a payment to a CANCELED item");
        }
        if (item.getStatus() == PaymentScheduleStatus.PAID) {
            throw new InvalidPaymentScheduleStateException("Item is already fully PAID");
        }
        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentInvalidAmountException("Payment amount must be greater than zero");
        }

        SchedulePayment payment = new SchedulePayment(
                tenantRepo.getReferenceById(tenantId),
                itemId,
                actorId,
                req.amount(),
                req.paidAt().atStartOfDay(),
                req.channel(),
                req.paymentReference(),
                req.notes()
        );
        SchedulePayment saved = paymentRepo.save(payment);

        // Recompute total and possibly mark PAID
        BigDecimal totalPaid = paymentRepo.sumPaidForItem(tenantId, itemId);
        if (totalPaid.compareTo(item.getAmount()) >= 0) {
            item.setStatus(PaymentScheduleStatus.PAID);
            itemRepo.save(item);
        }

        return PaymentResponse.from(saved);
    }

    // ── listPayments ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(UUID itemId) {
        UUID tenantId = requireTenantId();
        scheduleService.requireItem(tenantId, itemId); // ensure item belongs to tenant
        return paymentRepo.findByTenant_IdAndScheduleItemIdOrderByPaidAtAsc(tenantId, itemId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String buildSendBody(PaymentScheduleItem item) {
        return String.format(
                "Bonjour,%n%n" +
                "Nous vous adressons le présent appel de fonds concernant votre acquisition.%n%n" +
                "Échéance : %s%n" +
                "Libellé  : %s%n" +
                "Montant  : %s%n%n" +
                "Merci de procéder au règlement avant la date d'échéance.%n%n" +
                "Cordialement",
                item.getDueDate(),
                item.getLabel(),
                item.getAmount().toPlainString()
        );
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new CrossTenantAccessException("Missing tenant context");
        return id;
    }
}
