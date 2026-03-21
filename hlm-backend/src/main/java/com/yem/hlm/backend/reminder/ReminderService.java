package com.yem.hlm.backend.reminder;

import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.notification.domain.Notification;
import com.yem.hlm.backend.notification.domain.NotificationType;
import com.yem.hlm.backend.notification.repo.NotificationRepository;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for reminder workflows.
 *
 * <p>All methods are intentionally tenant-scoped and public for direct
 * invocation from unit tests or admin endpoints. The scheduler only orchestrates calls.
 *
 * <h3>Workflows</h3>
 * <ol>
 *   <li><b>Deposit due-date reminders</b> — EMAIL to agent, J-7, J-3, J-1 before {@code dueDate}
 *       when deposit is still PENDING.</li>
 *   <li><b>Prospect follow-up</b> — in-app notification to MANAGER/ADMIN when a PROSPECT or
 *       QUALIFIED_PROSPECT contact has had no activity for {@code prospectStaleDays} days.</li>
 * </ol>
 *
 * <p>Payment schedule item overdue reminders are handled by
 * {@link com.yem.hlm.backend.payments.service.ReminderService} (v2 payments module).
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private static final List<ContactStatus> PROSPECT_STATUSES =
            List.of(ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT);

    private final ReminderProperties props;
    private final DepositRepository depositRepository;
    private final ContactRepository contactRepository;
    private final OutboundMessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CommercialAuditRepository auditRepository;

    public ReminderService(ReminderProperties props,
                           DepositRepository depositRepository,
                           ContactRepository contactRepository,
                           OutboundMessageRepository messageRepository,
                           NotificationRepository notificationRepository,
                           UserRepository userRepository,
                           CommercialAuditRepository auditRepository) {
        this.props              = props;
        this.depositRepository  = depositRepository;
        this.contactRepository  = contactRepository;
        this.messageRepository  = messageRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository     = userRepository;
        this.auditRepository    = auditRepository;
    }

    // =========================================================================
    // F2.2-A: Deposit due-date reminders
    // =========================================================================

    /**
     * Sends EMAIL reminders to agents for deposits approaching their due date.
     * Creates one outbox message per deposit per warning interval.
     * Idempotent via correlationType check: skips deposits that already have a
     * DEPOSIT_REMINDER message PENDING or SENT for this correlationId today.
     */
    @Transactional
    public void runDepositDueReminders() {
        LocalDate today = LocalDate.now();

        for (int daysAhead : props.getDepositWarnDays()) {
            LocalDate targetDate = today.plusDays(daysAhead);
            LocalDateTime from = targetDate.atStartOfDay();
            LocalDateTime to   = targetDate.atTime(23, 59, 59);

            List<Deposit> deposits = depositRepository.findAllByStatusAndDueDateBetween(
                    DepositStatus.PENDING, from, to);

            for (Deposit deposit : deposits) {
                if (alreadyHasReminderMessage(deposit.getId())) {
                    continue;
                }
                User agent = deposit.getAgent();
                if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
                    log.warn("[REMINDER] Deposit {} has no agent email — skipping", deposit.getId());
                    continue;
                }
                String subject = String.format("Rappel réservation: échéance dans %d jour(s)", daysAhead);
                String body = String.format(
                        "Bonjour,\n\nLa réservation #%s expire le %s.\nVeuillez prendre les mesures nécessaires.\n\nCordialement,\nYEM CRM",
                        deposit.getId(), deposit.getDueDate());

                OutboundMessage msg = new OutboundMessage(
                        deposit.getSocieteId(), agent,
                        MessageChannel.EMAIL, agent.getEmail(),
                        subject, body);
                msg.setCorrelationType("DEPOSIT_REMINDER");
                msg.setCorrelationId(deposit.getId());
                messageRepository.save(msg);

                log.info("[REMINDER] Deposit due in {} days — queued email for agent={} deposit={}",
                        daysAhead, agent.getEmail(), deposit.getId());
            }
        }
    }

    // =========================================================================
    // F2.2-C: Prospect follow-up notifications
    // =========================================================================

    /**
     * Creates in-app notifications for MANAGER and ADMIN users when a prospect
     * contact has had no audit or message activity for {@code prospectStaleDays} days.
     */
    @Transactional
    public void runProspectFollowUp() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(props.getProspectStaleDays());

        // Work across all sociétés that have prospect contacts
        List<UUID> societeIds = contactRepository.findAll().stream()
                .filter(c -> PROSPECT_STATUSES.contains(c.getStatus()) && !c.isDeleted())
                .map(c -> c.getSocieteId())
                .distinct()
                .toList();

        log.info("[REMINDER] prospect follow-up: {} sociétés to check", societeIds.size());

        for (UUID societeId : societeIds) {
            try {
                processStaleProspectsForSociete(societeId, staleThreshold);
            } catch (Exception e) {
                log.error("[REMINDER] error processing prospects for societe={}: {}",
                        societeId, e.getMessage(), e);
            }
        }
    }

    private void processStaleProspectsForSociete(UUID societeId, LocalDateTime staleThreshold) {
        // Fetch contacts with prospect status
        var page = PageRequest.of(0, 500);
        var contacts = contactRepository.search(societeId, false, List.of(), null, null, page);

        // app_user_societe.role stores "ADMIN"/"MANAGER" without the ROLE_ prefix
        // (enforced by chk_societe_role CHECK constraint). Using the prefixed form
        // produces an empty result set — no notifications would ever be sent.
        List<User> managers = userRepository.findBySocieteIdAndRoleInAndEnabledTrue(
                societeId, Set.of("ADMIN", "MANAGER"));

        for (var contact : contacts) {
            if (!PROSPECT_STATUSES.contains(contact.getStatus()) || contact.isDeleted()) {
                continue;
            }
            // Check most recent audit/message activity
            List<UUID> contactIdList = List.of(contact.getId());
            boolean hasRecentAudit = auditRepository.findByTenantAndCorrelationIds(societeId, contactIdList)
                    .stream()
                    .anyMatch(e -> e.getOccurredAt().isAfter(staleThreshold));
            boolean hasRecentMessage = messageRepository.findByTenantAndCorrelationIds(societeId, contactIdList)
                    .stream()
                    .anyMatch(m -> m.getCreatedAt().isAfter(staleThreshold));

            if (!hasRecentAudit && !hasRecentMessage) {
                String payload = "{\"contactId\":\"" + contact.getId() + "\","
                        + "\"name\":\"" + contact.getFullName() + "\"}";
                for (User manager : managers) {
                    notificationRepository.save(new Notification(
                            contact.getSocieteId(), manager,
                            NotificationType.PROSPECT_STALE, contact.getId(), payload));
                }
                log.info("[REMINDER] Stale prospect {} notified {} managers",
                        contact.getId(), managers.size());
            }
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** True if a DEPOSIT_REMINDER message already exists (PENDING or SENT) for this deposit. */
    private boolean alreadyHasReminderMessage(UUID depositId) {
        return messageRepository.existsPendingOrSent(depositId, "DEPOSIT_REMINDER");
    }
}
