package com.yem.hlm.backend.tranche.domain;

/**
 * Lifecycle statuses for a Tranche (phased delivery group).
 * <p>
 * State machine:
 * <pre>
 * EN_PREPARATION → EN_COMMERCIALISATION → EN_TRAVAUX → ACHEVEE → LIVREE
 * </pre>
 * Each forward transition is gated in {@code TrancheService.advanceStatut()}.
 */
public enum TrancheStatut {

    /** Tranche being configured — not yet marketed or under construction. */
    EN_PREPARATION,

    /** Sales open for this tranche — construction not yet started. */
    EN_COMMERCIALISATION,

    /** Construction under way. */
    EN_TRAVAUX,

    /** Construction complete — awaiting regulatory sign-off and key handover. */
    ACHEVEE,

    /** Keys handed over to buyers — tranche fully delivered. Terminal state. */
    LIVREE
}
