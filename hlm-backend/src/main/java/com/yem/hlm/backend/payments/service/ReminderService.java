package com.yem.hlm.backend.payments.service;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import com.yem.hlm.backend.payments.domain.ReminderType;
import com.yem.hlm.backend.payments.domain.ScheduleItemReminder;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.ScheduleItemReminderRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily reminder processing for payment schedule items.
 *
 * <h3>Reminder types fired per run</h3>
 * <ul>
 *   <li>PRE_DUE_7D  — items due exactly 7 days from today</li>
 *   <li>PRE_DUE_1D  — items due exactly 1 day from today</li>
 *   <li>OVERDUE_1D  — items 1 day past due date</li>
 *   <li>OVERDUE_7D  — items 7 days past due date</li>
 *   <li>OVERDUE_30D — items 30 days past due date</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * Before queuing a message the service checks the reminder table (unique constraint
 * on item/type/channel/date) to skip duplicates for the same calendar day.
 *
 * <h3>Outbox integration</h3>
 * This service writes outbox rows directly (bypassing {@code MessageComposeService})
 * because the scheduler has no user session. The contract's agent is used as the
 * outbox {@code createdByUser} reference, and the buyer-email snapshot on the
 * contract is used as the recipient.
 */
@Service("paymentsScheduleReminderService")
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final PaymentScheduleItemRepository  itemRepo;
    private final SchedulePaymentRepository      paymentRepo;
    private final ScheduleItemReminderRepository reminderRepo;
    private final SaleContractRepository         contractRepo;
    private final OutboundMessageRepository      outboxRepo;
    private final TenantRepository               tenantRepo;

    public ReminderService(PaymentScheduleItemRepository itemRepo,
                           SchedulePaymentRepository paymentRepo,
                           ScheduleItemReminderRepository reminderRepo,
                           SaleContractRepository contractRepo,
                           OutboundMessageRepository outboxRepo,
                           TenantRepository tenantRepo) {
        this.itemRepo      = itemRepo;
        this.paymentRepo   = paymentRepo;
        this.reminderRepo  = reminderRepo;
        this.contractRepo  = contractRepo;
        this.outboxRepo    = outboxRepo;
        this.tenantRepo    = tenantRepo;
    }

    /**
     * Main entry point called by the scheduler.
     * Iterates all tenants that have active items and runs the reminder logic.
     */
    @Transactional
    public void processAll() {
        LocalDate today = LocalDate.now();
        List<UUID> tenantIds = itemRepo.findActiveTenantIds();
        log.info("ReminderService: processing {} active tenants for date {}", tenantIds.size(), today);

        for (UUID tenantId : tenantIds) {
            try {
                processTenant(tenantId, today);
            } catch (Exception ex) {
                // Isolate per-tenant failures — do not abort other tenants
                log.error("ReminderService: error processing tenant {}: {}", tenantId, ex.getMessage(), ex);
            }
        }
    }

    // =========================================================================
    // Per-tenant processing
    // =========================================================================

    private void processTenant(UUID tenantId, LocalDate today) {
        markOverdue(tenantId, today);
        firePreDueReminders(tenantId, today, 7, ReminderType.PRE_DUE_7D);
        firePreDueReminders(tenantId, today, 1, ReminderType.PRE_DUE_1D);
        fireOverdueReminders(tenantId, today, 1,  ReminderType.OVERDUE_1D);
        fireOverdueReminders(tenantId, today, 7,  ReminderType.OVERDUE_7D);
        fireOverdueReminders(tenantId, today, 30, ReminderType.OVERDUE_30D);
    }

    // ── Overdue marking ───────────────────────────────────────────────────────

    private void markOverdue(UUID tenantId, LocalDate today) {
        List<PaymentScheduleItem> candidates = itemRepo.findOverdueCandidates(tenantId, today);
        for (PaymentScheduleItem item : candidates) {
            BigDecimal paid = paymentRepo.sumPaidForItem(tenantId, item.getId());
            if (paid.compareTo(item.getAmount()) >= 0) {
                item.setStatus(PaymentScheduleStatus.PAID);
            } else if (item.getStatus() != PaymentScheduleStatus.OVERDUE) {
                item.setStatus(PaymentScheduleStatus.OVERDUE);
            }
            itemRepo.save(item);
        }
    }

    // ── Pre-due reminders ─────────────────────────────────────────────────────

    private void firePreDueReminders(UUID tenantId, LocalDate today,
                                     int daysAhead, ReminderType type) {
        LocalDate targetDueDate = today.plusDays(daysAhead);
        List<PaymentScheduleItem> items = itemRepo.findDueOn(tenantId, targetDueDate);
        for (PaymentScheduleItem item : items) {
            BigDecimal remaining = item.getAmount().subtract(
                    paymentRepo.sumPaidForItem(tenantId, item.getId()));
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            String body    = buildPreDueBody(item, daysAhead);
            String subject = "Rappel \u2013 Appel de fonds dans " + daysAhead + " jour(s)";
            queueIfNotSent(tenantId, item, type, MessageChannel.EMAIL, today, subject, body);
        }
    }

    // ── Overdue reminders ─────────────────────────────────────────────────────

    private void fireOverdueReminders(UUID tenantId, LocalDate today,
                                      int daysPast, ReminderType type) {
        LocalDate targetDueDate = today.minusDays(daysPast);
        List<PaymentScheduleItem> items = itemRepo.findDueOn(tenantId, targetDueDate);
        for (PaymentScheduleItem item : items) {
            if (item.getStatus() == PaymentScheduleStatus.PAID
                    || item.getStatus() == PaymentScheduleStatus.CANCELED) continue;
            BigDecimal remaining = item.getAmount().subtract(
                    paymentRepo.sumPaidForItem(tenantId, item.getId()));
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            String body    = buildOverdueBody(item, daysPast, remaining);
            String subject = "Relance \u2013 Appel de fonds en retard (" + daysPast + " jour(s))";
            queueIfNotSent(tenantId, item, type, MessageChannel.EMAIL, today, subject, body);
        }
    }

    // ── Queue with idempotency ────────────────────────────────────────────────

    /**
     * Queues an outbox row directly using the contract agent as actor and the
     * buyer-email snapshot as recipient. Skips if an identical reminder already
     * exists for this calendar day (idempotency).
     */
    private void queueIfNotSent(UUID tenantId, PaymentScheduleItem item,
                                 ReminderType type, MessageChannel channel,
                                 LocalDate reminderDate, String subject, String body) {
        if (reminderRepo.existsByScheduleItemIdAndReminderTypeAndChannelAndReminderDate(
                item.getId(), type, channel, reminderDate)) {
            log.debug("Reminder already sent: item={} type={} date={}", item.getId(), type, reminderDate);
            return;
        }

        // Resolve contract to get buyer recipient + agent as actor
        SaleContract contract = contractRepo
                .findByTenant_IdAndId(tenantId, item.getContractId())
                .orElse(null);
        if (contract == null) {
            log.warn("Contract {} not found for item {}; skipping reminder",
                    item.getContractId(), item.getId());
            return;
        }

        String recipient = (channel == MessageChannel.EMAIL)
                ? contract.getBuyerEmail()
                : contract.getBuyerPhone();
        if (recipient == null || recipient.isBlank()) {
            log.debug("No {} on contract {} for item {}; skipping",
                    channel, item.getContractId(), item.getId());
            return;
        }

        try {
            // Write directly to outbox — scheduler has no user session.
            // Contract agent is used as createdByUser.
            OutboundMessage msg = new OutboundMessage(
                    tenantRepo.getReferenceById(tenantId),
                    contract.getAgent(),
                    channel,
                    recipient.trim(),
                    subject,
                    body
            );
            msg.setCorrelationType("PAYMENT_SCHEDULE_ITEM");
            msg.setCorrelationId(item.getId());
            outboxRepo.save(msg);
        } catch (Exception ex) {
            log.warn("Could not queue reminder for item {}: {}", item.getId(), ex.getMessage());
            return;
        }

        // Record idempotency guard
        reminderRepo.save(new ScheduleItemReminder(
                tenantRepo.getReferenceById(tenantId),
                item.getId(), type, channel, reminderDate));

        log.info("Reminder queued: item={} type={} channel={} date={}",
                item.getId(), type, channel, reminderDate);
    }

    // ── Message body builders ─────────────────────────────────────────────────

    private String buildPreDueBody(PaymentScheduleItem item, int daysAhead) {
        return String.format(
                "Bonjour,%n%nNous vous rappelons que votre appel de fonds arrive à échéance " +
                "dans %d jour(s).%n%n" +
                "Libellé  : %s%n" +
                "Montant  : %s%n" +
                "Échéance : %s%n%n" +
                "Merci de vous assurer que le règlement sera effectué avant la date d'échéance.%n%n" +
                "Cordialement",
                daysAhead, item.getLabel(),
                item.getAmount().toPlainString(),
                item.getDueDate()
        );
    }

    private String buildOverdueBody(PaymentScheduleItem item, int daysPast, BigDecimal remaining) {
        return String.format(
                "Bonjour,%n%nMalgré nos précédents rappels, le règlement suivant n'a pas encore " +
                "été reçu (retard de %d jour(s)).%n%n" +
                "Libellé         : %s%n" +
                "Montant restant : %s%n" +
                "Échéance        : %s%n%n" +
                "Nous vous remercions de régulariser cette situation dans les meilleurs délais.%n%n" +
                "Cordialement",
                daysPast, item.getLabel(),
                remaining.toPlainString(),
                item.getDueDate()
        );
    }
}
