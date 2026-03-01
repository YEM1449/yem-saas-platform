package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.contact.api.dto.TimelineEventResponse;
import com.yem.hlm.backend.contact.api.dto.TimelineEventResponse.TimelineCategory;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.notification.repo.NotificationRepository;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a unified, chronologically sorted activity timeline for a contact.
 *
 * <p>Sources:
 * <ol>
 *   <li>CommercialAuditRepository — lifecycle events on deposits/contracts linked to the contact.</li>
 *   <li>OutboundMessageRepository — outbox messages correlated to the contact's deposits/contracts.</li>
 *   <li>NotificationRepository — in-app notifications whose refId matches the contact's deposits/contracts.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class ContactTimelineService {

    private final ContactRepository contactRepository;
    private final DepositRepository depositRepository;
    private final SaleContractRepository contractRepository;
    private final CommercialAuditRepository auditRepository;
    private final OutboundMessageRepository messageRepository;
    private final NotificationRepository notificationRepository;

    public ContactTimelineService(ContactRepository contactRepository,
                                  DepositRepository depositRepository,
                                  SaleContractRepository contractRepository,
                                  CommercialAuditRepository auditRepository,
                                  OutboundMessageRepository messageRepository,
                                  NotificationRepository notificationRepository) {
        this.contactRepository    = contactRepository;
        this.depositRepository    = depositRepository;
        this.contractRepository   = contractRepository;
        this.auditRepository      = auditRepository;
        this.messageRepository    = messageRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Returns the activity timeline for a contact, capped at {@code limit} entries.
     *
     * @param contactId the contact UUID
     * @param limit     maximum events to return (default 50)
     * @return sorted list of timeline events, newest first
     * @throws ContactNotFoundException if the contact does not belong to the current tenant
     */
    public List<TimelineEventResponse> getTimeline(UUID contactId, int limit) {
        UUID tenantId = TenantContext.getTenantId();

        // Guard: confirm contact belongs to this tenant
        contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        // Collect all related entity IDs
        Set<UUID> correlationIds = new HashSet<>();
        correlationIds.add(contactId); // messages may be correlated directly to contact

        // All deposits for this contact (all statuses via the flexible report query)
        depositRepository.report(tenantId, null, null, contactId, null, null, null)
                .forEach(d -> correlationIds.add(d.getId()));

        // All contracts where this contact is the buyer
        contractRepository.filter(tenantId, null, null, null, null, null).stream()
                .filter(c -> c.getBuyerContact() != null
                        && contactId.equals(c.getBuyerContact().getId()))
                .forEach(c -> correlationIds.add(c.getId()));

        List<TimelineEventResponse> events = new ArrayList<>();

        // 1. Audit events
        if (!correlationIds.isEmpty()) {
            auditRepository.findByTenantAndCorrelationIds(tenantId, correlationIds).stream()
                    .map(e -> new TimelineEventResponse(
                            e.getOccurredAt(),
                            e.getEventType().name(),
                            TimelineCategory.AUDIT,
                            formatAuditSummary(e.getEventType().name()),
                            e.getCorrelationId()
                    ))
                    .forEach(events::add);

            // 2. Outbox messages
            messageRepository.findByTenantAndCorrelationIds(tenantId, correlationIds).stream()
                    .map(m -> new TimelineEventResponse(
                            m.getCreatedAt(),
                            m.getChannel().name() + "_" + m.getStatus().name(),
                            TimelineCategory.MESSAGE,
                            formatMessageSummary(m.getChannel().name(), m.getRecipient()),
                            m.getId()
                    ))
                    .forEach(events::add);

            // 3. Notifications
            notificationRepository.findByTenantAndRefIds(tenantId, correlationIds).stream()
                    .map(n -> new TimelineEventResponse(
                            n.getCreatedAt(),
                            n.getType().name(),
                            TimelineCategory.NOTIFICATION,
                            n.getType().name().replace('_', ' '),
                            n.getRefId()
                    ))
                    .forEach(events::add);
        }

        // Sort newest-first and cap
        events.sort(Comparator.comparing(TimelineEventResponse::timestamp).reversed());
        return events.stream().limit(limit).toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String formatAuditSummary(String eventType) {
        return switch (eventType) {
            case "DEPOSIT_CREATED"   -> "Réservation créée";
            case "DEPOSIT_CONFIRMED" -> "Réservation confirmée";
            case "DEPOSIT_CANCELED"  -> "Réservation annulée";
            case "DEPOSIT_EXPIRED"   -> "Réservation expirée";
            case "CONTRACT_CREATED"  -> "Contrat créé";
            case "CONTRACT_SIGNED"   -> "Contrat signé";
            case "CONTRACT_CANCELED" -> "Contrat annulé";
            case "PAYMENT_CALL_ISSUED" -> "Appel de fonds émis";
            case "PAYMENT_RECEIVED"    -> "Paiement reçu";
            default -> eventType.replace('_', ' ');
        };
    }

    private String formatMessageSummary(String channel, String recipient) {
        return channel + " envoyé à " + recipient;
    }
}
