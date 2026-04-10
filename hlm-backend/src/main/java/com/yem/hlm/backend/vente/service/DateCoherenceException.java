package com.yem.hlm.backend.vente.service;

import java.util.List;

/**
 * Thrown when one or more date coherence rules are violated in a vente or écheance request.
 * Contains a structured list of violations for client display.
 */
public class DateCoherenceException extends RuntimeException {

    private final List<DateCoherenceViolation> violations;

    public DateCoherenceException(List<DateCoherenceViolation> violations) {
        super("Date coherence violation(s): " + violations);
        this.violations = violations;
    }

    public List<DateCoherenceViolation> getViolations() {
        return violations;
    }
}
