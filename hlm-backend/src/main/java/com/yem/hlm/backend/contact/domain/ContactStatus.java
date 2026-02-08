package com.yem.hlm.backend.contact.domain;

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
    LOST
}
