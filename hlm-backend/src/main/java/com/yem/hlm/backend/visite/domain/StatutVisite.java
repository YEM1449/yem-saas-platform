package com.yem.hlm.backend.visite.domain;

import java.util.Set;

/**
 * Lifecycle of a {@link Visite} (RG-V02).
 *
 * <pre>
 *   PLANIFIEE → CONFIRMEE → REALISEE (terminal, requires compte-rendu)
 *   PLANIFIEE/CONFIRMEE → ANNULEE (terminal)
 *   CONFIRMEE → NO_SHOW (terminal)
 * </pre>
 */
public enum StatutVisite {
    PLANIFIEE,
    CONFIRMEE,
    REALISEE,
    ANNULEE,
    NO_SHOW;

    /** Allowed forward transitions from this state. Empty = terminal. */
    public Set<StatutVisite> allowedTransitions() {
        return switch (this) {
            case PLANIFIEE -> Set.of(CONFIRMEE, ANNULEE);
            case CONFIRMEE -> Set.of(REALISEE, ANNULEE, NO_SHOW);
            case REALISEE, ANNULEE, NO_SHOW -> Set.of();
        };
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    public boolean canTransitionTo(StatutVisite target) {
        return allowedTransitions().contains(target);
    }
}
