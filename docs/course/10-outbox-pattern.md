# Module 10 — Outbox Pattern

## Learning Objectives

- Explain the "lost message" problem with direct SMTP sends inside transactions
- Describe the transactional outbox solution used in this codebase
- Identify the `FOR UPDATE SKIP LOCKED` pattern and why it matters

---

## The Problem

Sending an email directly inside a `@Transactional` method has a fatal flaw:

```java
@Transactional
public void confirmDeposit(UUID depositId) {
    deposit.confirm();
    depositRepo.save(deposit);
    emailSender.send(buyer.getEmail(), "Deposit confirmed", body);  // SMTP call
    // If an exception occurs HERE, the transaction rolls back.
    // But the email was already sent!
}
```

If the transaction rolls back after the SMTP call, the buyer receives a confirmation email for a deposit that was never saved. Conversely, if the SMTP server is temporarily down, the email is lost even if the deposit was saved.

---

## The Solution: Transactional Outbox

Instead of sending emails directly, write a row to the `outbound_message` table within the same transaction:

```java
@Transactional
public void confirmDeposit(UUID depositId) {
    deposit.confirm();
    depositRepo.save(deposit);
    outboxService.queue(buyer.getEmail(), "Deposit confirmed", body);  // INSERT only
    // If the transaction commits, the outbox row exists.
    // If it rolls back, the outbox row is also rolled back — no email sent.
}
```

A separate background process (`OutboundDispatcherService`) polls the outbox table and sends emails:

```java
@Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms}")
@Transactional
public void runDispatch() {
    List<OutboundMessage> batch = repo.claimPendingBatch(batchSize);
    for (OutboundMessage msg : batch) {
        try {
            emailSender.send(msg.getTo(), msg.getSubject(), msg.getBody());
            msg.markSent();
        } catch (Exception e) {
            msg.recordFailure(e.getMessage(), nextRetryAt(msg.getRetryCount()));
        }
    }
}
```

---

## FOR UPDATE SKIP LOCKED

The native query to claim messages:

```sql
SELECT * FROM outbound_message
WHERE status = 'PENDING' AND next_retry_at <= now()
ORDER BY created_at
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

`FOR UPDATE SKIP LOCKED` is a PostgreSQL feature for concurrent-safe work queues:
- `FOR UPDATE` — locks the selected rows to prevent other transactions from processing the same messages.
- `SKIP LOCKED` — skips rows that are already locked by another transaction (instead of waiting).

This allows multiple application instances to process the outbox concurrently without double-sending.

---

## Exponential Backoff

On failure, the next retry time is calculated:

```
retry 0 → next_retry_at = now + 1 minute
retry 1 → next_retry_at = now + 5 minutes
retry 2 → next_retry_at = now + 30 minutes
retry 3 → status = FAILED (permanent)
```

`maxRetries` is configurable via `OUTBOX_MAX_RETRIES` (default 3).

---

## Exception: Magic Link Emails

Portal magic link emails bypass the outbox and call `EmailSender.send()` directly. Why? The magic link endpoint is public — there is no active `@Transactional` context and no `app_user` FK to attach to an outbox row.

---

## Source Files

| File | Purpose |
|------|---------|
| `outbox/service/OutboxMessageService.java` | Writes outbox rows |
| `outbox/service/OutboundDispatcherService.java` | Polls and dispatches |
| `outbox/repo/OutboundMessageRepository.java` | Native SKIP LOCKED query |
| `outbox/domain/OutboundMessage.java` | Entity with retry logic |

---

## Exercise

1. Open `OutboundMessageRepository.java` and find the native query with `FOR UPDATE SKIP LOCKED`.
2. Verify the query is annotated with `@Lock` or uses `nativeQuery = true`.
3. Open `OutboundDispatcherService.java` and trace the retry backoff calculation.
4. Open `portal/auth/service/PortalAuthService.java` and find the direct `emailSender.send(...)` call (bypassing the outbox). Confirm it is called outside any `@Transactional` method.
