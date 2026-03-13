package com.yem.hlm.backend.reservation.domain;

/**
 * Lifecycle status of a lightweight property reservation.
 * A Reservation is a pre-deposit "intent to buy" that reserves a property
 * without requiring an immediate financial commitment.
 * <p>
 * State machine:
 * ACTIVE → EXPIRED (scheduler)
 * ACTIVE → CANCELLED (manual)
 * ACTIVE → CONVERTED_TO_DEPOSIT (via convertToDeposit())
 */
public enum ReservationStatus {
    /** Reservation is active — property is held for this contact. */
    ACTIVE,
    /** Reservation expired past its expiry date without conversion. */
    EXPIRED,
    /** Reservation was manually cancelled. */
    CANCELLED,
    /** Reservation was converted into a formal Deposit. */
    CONVERTED_TO_DEPOSIT
}
