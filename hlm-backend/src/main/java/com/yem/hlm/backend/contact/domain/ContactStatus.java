package com.yem.hlm.backend.contact.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ContactStatus {
    // Prospect workflow
    PROSPECT,
    QUALIFIED_PROSPECT,

    // Client workflow
    CLIENT,
    ACTIVE_CLIENT,
    COMPLETED_CLIENT,
    REFERRAL,

    // Terminal
    LOST;

    private static final Map<ContactStatus, Set<ContactStatus>> ALLOWED_TRANSITIONS = Map.of(
            PROSPECT, EnumSet.of(QUALIFIED_PROSPECT, LOST),
            QUALIFIED_PROSPECT, EnumSet.of(PROSPECT, CLIENT, LOST),
            CLIENT, EnumSet.of(ACTIVE_CLIENT, COMPLETED_CLIENT, LOST),
            ACTIVE_CLIENT, EnumSet.of(COMPLETED_CLIENT, LOST),
            COMPLETED_CLIENT, EnumSet.of(REFERRAL),
            REFERRAL, EnumSet.noneOf(ContactStatus.class),
            LOST, EnumSet.of(PROSPECT)
    );

    public boolean canTransitionTo(ContactStatus target) {
        Set<ContactStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
