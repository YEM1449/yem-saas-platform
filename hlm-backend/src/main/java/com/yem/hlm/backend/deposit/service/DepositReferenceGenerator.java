package com.yem.hlm.backend.deposit.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class DepositReferenceGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DepositReferenceGenerator() {}

    static String generate(LocalDateTime now) {
        String ts = now.format(TS);
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "DEP-" + ts + "-" + shortId;
    }
}
