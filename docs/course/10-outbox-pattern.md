# Module 10: Outbox Pattern

## Why This Matters

External delivery should not make a sales or admin transaction fail unpredictably.

## Learning Goals

- understand why outbound messages are queued
- understand how retries work
- understand how concurrency safety is achieved

## Pattern Summary

1. business action creates an `outbound_message`
2. the main transaction commits
3. a scheduler polls pending messages
4. provider-specific sending happens asynchronously
5. status is updated to sent or failed

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/outbox/service/OutboundDispatcherService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/outbox/service/OutboundDispatcherService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/outbox/scheduler/OutboundDispatcherScheduler.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/outbox/scheduler/OutboundDispatcherScheduler.java)
- [../guides/engineer/object-storage.md](../guides/engineer/object-storage.md)

## Things To Notice

- `FOR UPDATE SKIP LOCKED` prevents duplicate claims
- retry timing is explicit
- this is a reliability pattern, not just a convenience layer

## Exercise

Imagine the email provider is down for 15 minutes. Describe how the outbox pattern protects the user-facing workflow.
