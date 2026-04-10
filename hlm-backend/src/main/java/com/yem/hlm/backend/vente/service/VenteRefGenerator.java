package com.yem.hlm.backend.vente.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates human-readable, globally unique vente references.
 *
 * <p>Format: {@code VTE-{YEAR}-{CODE3}-{SEQ05}}
 * <br>Example: {@code VTE-2026-1F3-00001}
 *
 * <p>The sequence counter is stored in {@code vente_ref_counter}
 * (one row per société). A single atomic UPSERT + RETURNING statement
 * prevents race conditions under concurrent inserts.
 */
@Service
public class VenteRefGenerator {

    private final JdbcTemplate jdbc;

    public VenteRefGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Allocates the next sequence number for {@code societeId} and returns
     * a formatted vente reference. Must be called inside an active transaction.
     */
    public String generate(UUID societeId) {
        Long seq = jdbc.queryForObject(
                """
                INSERT INTO vente_ref_counter (societe_id, last_seq)
                VALUES (?, 1)
                ON CONFLICT (societe_id) DO UPDATE
                  SET last_seq = vente_ref_counter.last_seq + 1
                RETURNING last_seq
                """,
                Long.class,
                societeId
        );

        int year = LocalDate.now().getYear();
        String code = societeId.toString().replace("-", "").substring(0, 3).toUpperCase();
        return "VTE-%d-%s-%05d".formatted(year, code, seq);
    }
}
