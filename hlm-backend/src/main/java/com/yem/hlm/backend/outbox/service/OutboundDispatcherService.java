package com.yem.hlm.backend.outbox.service;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.outbox.service.provider.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Polls PENDING outbound messages and dispatches them via configured providers.
 *
 * <p><b>Concurrency safety:</b> {@link OutboundMessageRepository#fetchPendingBatch}
 * uses {@code FOR UPDATE SKIP LOCKED} so multiple scheduler instances (or threads)
 * never process the same row.
 *
 * <p><b>Retry policy:</b> exponential backoff — attempts at +1 min, +5 min, +30 min.
 * After {@code maxRetries} the message is marked {@code FAILED} and skipped permanently.
 *
 * <p><b>Transaction scope:</b> the entire batch runs in one transaction. This is
 * acceptable for Noop providers (no latency). For real SMTP/SMS providers, extract
 * the provider call outside the transaction and use a two-phase claim approach
 * (e.g., mark PROCESSING, commit, send, mark SENT/FAILED).
 */
@Service
public class OutboundDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(OutboundDispatcherService.class);

    /** Exponential backoff: attempt[0]=+1m, attempt[1]=+5m, attempt[2]=+30m, …+30m */
    private static final long[] BACKOFF_MINUTES = {1L, 5L, 30L};

    private final OutboundMessageRepository messageRepo;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    @Value("${app.outbox.batch-size:20}")
    private int batchSize;

    @Value("${app.outbox.max-retries:3}")
    private int maxRetries;

    public OutboundDispatcherService(OutboundMessageRepository messageRepo,
                                     EmailSender emailSender,
                                     SmsSender smsSender) {
        this.messageRepo = messageRepo;
        this.emailSender = emailSender;
        this.smsSender   = smsSender;
    }

    // =========================================================================
    // Dispatch entry point (called by scheduler)
    // =========================================================================

    /**
     * Fetches up to {@code batchSize} PENDING messages with {@code SKIP LOCKED} and
     * dispatches each one, updating status in the same transaction.
     */
    @Transactional
    public void runDispatch() {
        List<UUID> ids = messageRepo.fetchPendingBatch(batchSize);
        if (ids.isEmpty()) return;

        log.debug("Dispatching {} pending outbound message(s)", ids.size());

        for (UUID id : ids) {
            if (id != null) dispatchOne(id);
        }
    }

    // =========================================================================
    // Per-message dispatch
    // =========================================================================

    private void dispatchOne(UUID id) {
        OutboundMessage msg = messageRepo.findById(id).orElse(null);
        if (msg == null) return; // already deleted / race condition

        try {
            if (msg.getChannel() == MessageChannel.EMAIL) {
                emailSender.send(
                        msg.getRecipient(),
                        msg.getSubject() != null ? msg.getSubject() : "",
                        msg.getBody()
                );
            } else {
                smsSender.send(msg.getRecipient(), msg.getBody());
            }
            msg.markSent();
            log.info("Message {} [{}] sent to {}", id, msg.getChannel(), msg.getRecipient());
        } catch (Exception e) {
            log.warn("Message {} dispatch failed (attempt {}): {}",
                    id, msg.getRetriesCount() + 1, e.getMessage());

            LocalDateTime nextRetry = msg.getRetriesCount() < maxRetries - 1
                    ? computeNextRetry(msg.getRetriesCount())
                    : null; // null → mark as FAILED permanently

            msg.recordFailure(e.getMessage(), nextRetry);
        }

        messageRepo.save(msg);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LocalDateTime computeNextRetry(int currentRetries) {
        int idx = Math.min(currentRetries, BACKOFF_MINUTES.length - 1);
        return LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[idx]);
    }
}
