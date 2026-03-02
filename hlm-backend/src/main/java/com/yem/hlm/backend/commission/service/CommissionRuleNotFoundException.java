package com.yem.hlm.backend.commission.service;

import java.util.UUID;

public class CommissionRuleNotFoundException extends RuntimeException {
    public CommissionRuleNotFoundException(UUID id) {
        super("Commission rule not found: " + id);
    }
}
