package com.yem.hlm.backend.outbox.domain;

/** Lifecycle status of an outbound message in the outbox. */
public enum MessageStatus {
    /** Queued; not yet dispatched. */
    PENDING,
    /** Successfully delivered to the provider. */
    SENT,
    /** All retry attempts exhausted without success. */
    FAILED
}
