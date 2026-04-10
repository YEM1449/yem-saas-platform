package com.yem.hlm.backend.reservation.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates human-readable, globally unique reservation references.
 *
 * <p>Format: {@code RES-{YEAR}-{CODE3}-{SEQ05}}
 * <br>Example: {@code RES-2026-1F3-00042}
 *
 * <p>The sequence counter is stored in {@code reservation_ref_counter}
 * (one row per société). A single atomic UPSERT + RETURNING statement
 * prevents race conditions under concurrent inserts.
 */
@Service
public class ReservationRefGenerator {

    private final JdbcTemplate jdbc;

    public ReservationRefGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Allocates the next sequence number for {@code societeId} and returns
     * a formatted reservation reference. Must be called inside an active transaction.
     */
    public String generate(UUID societeId) {
        Long seq = jdbc.queryForObject(
                """
                INSERT INTO reservation_ref_counter (societe_id, last_seq)
                VALUES (?, 1)
                ON CONFLICT (societe_id) DO UPDATE
                  SET last_seq = reservation_ref_counter.last_seq + 1
                RETURNING last_seq
                """,
                Long.class,
                societeId
        );

        int year = LocalDate.now().getYear();
        // 3-char société code derived from the UUID (stable, no extra DB lookup needed)
        String code = societeId.toString().replace("-", "").substring(0, 3).toUpperCase();
        return "RES-%d-%s-%05d".formatted(year, code, seq);
    }
}
