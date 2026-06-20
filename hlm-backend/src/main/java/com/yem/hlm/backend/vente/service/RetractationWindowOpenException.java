package com.yem.hlm.backend.vente.service;

import java.time.LocalDate;

/**
 * Thrown when an attempt is made to advance a vente OUT of {@code EN_RETRACTATION} toward a forward
 * stage (e.g. {@code ACOMPTE}) while the buyer's legal cooling-off window is still open.
 *
 * <p>The buyer's right of withdrawal (Loi 44-00 Art. 618-3) must run its full course: until the
 * deadline has passed, the only permitted exits from {@code EN_RETRACTATION} are the buyer's own
 * withdrawal ({@code → ANNULE}, via {@code exerciseRetractation}) or the scheduled closing of the
 * window once the deadline elapses ({@code → RESERVE}, via {@code closeExpiredRetractations}).
 * Letting staff advance the pipeline early would defeat that right (EX-011 / DA-011).
 *
 * <p>Mapped to HTTP 409 (RETRACTATION_WINDOW_OPEN).
 */
public class RetractationWindowOpenException extends RuntimeException {
    public RetractationWindowOpenException(LocalDate deadline) {
        super(deadline != null
                ? "Le délai légal de rétractation court jusqu'au " + deadline
                  + " : la vente ne peut pas avancer avant cette date (Art. 618-3 Loi 44-00)."
                : "Le délai légal de rétractation n'est pas clos : la vente ne peut pas avancer "
                  + "(Art. 618-3 Loi 44-00).");
    }
}
