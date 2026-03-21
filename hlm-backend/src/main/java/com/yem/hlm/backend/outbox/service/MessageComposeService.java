package com.yem.hlm.backend.outbox.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.outbox.api.dto.MessageResponse;
import com.yem.hlm.backend.outbox.api.dto.SendMessageRequest;
import com.yem.hlm.backend.outbox.api.dto.SendMessageResponse;
import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.MessageStatus;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Creates PENDING outbound messages and provides the tenant-scoped list.
 *
 * <p>Request path: validate + insert PENDING row only (fast).
 * Actual dispatch is handled asynchronously by {@code OutboundDispatcherService}.
 */
@Service
public class MessageComposeService {

    // Very permissive email regex — full RFC 5321 is overkill for CRM usage.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OutboundMessageRepository messageRepo;
    private final ContactRepository contactRepo;
    private final UserRepository userRepo;

    public MessageComposeService(OutboundMessageRepository messageRepo,
                                 ContactRepository contactRepo,
                                 UserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.contactRepo = contactRepo;
        this.userRepo    = userRepo;
    }

    // =========================================================================
    // Compose
    // =========================================================================

    /**
     * Validates the request, resolves the recipient, and persists a PENDING outbox row.
     *
     * @return response containing the new {@code messageId}
     */
    @Transactional
    public SendMessageResponse compose(SendMessageRequest req) {
        UUID societeId = requireSocieteId();
        UUID userId    = requireUserId();

        String recipient = resolveRecipient(req, societeId);

        var msg = new OutboundMessage(
                societeId,
                userRepo.getReferenceById(userId),
                req.channel(),
                recipient,
                req.subject(),
                req.body()
        );

        if (req.correlationType() != null) {
            msg.setCorrelationType(req.correlationType());
        }
        if (req.correlationId() != null) {
            msg.setCorrelationId(req.correlationId());
        }

        OutboundMessage saved = messageRepo.save(msg);
        return new SendMessageResponse(saved.getId());
    }

    // =========================================================================
    // List
    // =========================================================================

    /**
     * Returns a paged, tenant-scoped list of outbound messages.
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> list(MessageChannel channel,
                                      MessageStatus status,
                                      UUID contactId,
                                      LocalDateTime from,
                                      LocalDateTime to,
                                      int page,
                                      int size) {
        UUID societeId = requireSocieteId();
        // contactId filter: messages composed for a specific contact are stored
        // with correlationType=CONTACT and correlationId=contactId.
        // We accept contactId as a convenience filter mapped to correlationId.
        return messageRepo.findByTenant(
                societeId, channel, status, contactId, from, to,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200))
        ).map(this::toResponse);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private String resolveRecipient(SendMessageRequest req, UUID societeId) {
        if (req.contactId() != null) {
            Contact contact = contactRepo.findBySocieteIdAndId(societeId, req.contactId())
                    .orElseThrow(() -> new ContactNotFoundException(req.contactId()));

            if (req.channel() == MessageChannel.EMAIL) {
                if (contact.getEmail() == null || contact.getEmail().isBlank()) {
                    throw new ContactChannelMissingException(
                            "Contact " + req.contactId() + " has no email address");
                }
                return contact.getEmail().trim();
            } else {
                if (contact.getPhone() == null || contact.getPhone().isBlank()) {
                    throw new ContactChannelMissingException(
                            "Contact " + req.contactId() + " has no phone number");
                }
                return contact.getPhone().trim();
            }
        }

        // Explicit recipient supplied
        if (req.recipient() == null || req.recipient().isBlank()) {
            throw new InvalidRecipientException(
                    "Either contactId or recipient must be provided");
        }
        String r = req.recipient().trim();
        if (req.channel() == MessageChannel.EMAIL && !EMAIL_PATTERN.matcher(r).matches()) {
            throw new InvalidRecipientException("Invalid email address: " + r);
        }
        return r;
    }

    private MessageResponse toResponse(OutboundMessage m) {
        return new MessageResponse(
                m.getId(),
                m.getChannel(),
                m.getStatus(),
                m.getRecipient(),
                m.getSubject(),
                m.getBody(),
                m.getCreatedAt(),
                m.getSentAt(),
                m.getRetriesCount(),
                m.getLastError(),
                m.getCorrelationType(),
                m.getCorrelationId(),
                m.getCreatedByUser().getId()
        );
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new CrossTenantAccessException("Missing société context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = SocieteContext.getUserId();
        if (id == null) throw new CrossTenantAccessException("Missing user context");
        return id;
    }
}
