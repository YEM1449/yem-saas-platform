package com.yem.hlm.backend.legal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Market-aware legal constants (VEFA). Values are driven by the {@code MARKET_CODE}
 * environment variable so legal thresholds are never hardcoded in business logic.
 *
 * <p>Reference: {@code docs/legal/loi-44-00-constantes.md} (Maroc, Loi 44-00).
 * Additive and side-effect-free — safe to introduce regardless of the pipeline
 * approach chosen for Wave 12.
 */
@Component
public class MarketConfig {

    @Value("${app.market.code:MA}")
    private String marketCode;

    /** Legal cooling-off period in days (Loi 44-00 Art. 618-3 = 7 ; Loi SRU FR = 10). */
    public int getDelaiRetractationJours() {
        return switch (marketCode) {
            case "FR" -> 10;
            case "MA" -> 7;
            default   -> 7;
        };
    }

    /**
     * Time zone of the active market's legal jurisdiction. Legal deadlines (cooling-off,
     * option holds, delivery-reserve windows) MUST be computed in this zone — never the JVM
     * default — so a server running UTC does not produce an off-by-one on a date-precise legal
     * right (EX-009). Morocco is UTC+1 year-round; France is Europe/Paris (with DST).
     * Override via {@code app.market.zone-id} if the deployment needs a non-default zone.
     */
    public ZoneId getZoneId() {
        if (zoneIdOverride != null && !zoneIdOverride.isBlank()) {
            return ZoneId.of(zoneIdOverride.trim());
        }
        return switch (marketCode) {
            case "FR" -> ZoneId.of("Europe/Paris");
            case "MA" -> ZoneId.of("Africa/Casablanca");
            default   -> ZoneId.of("Africa/Casablanca");
        };
    }

    @Value("${app.market.zone-id:}")
    private String zoneIdOverride;

    /** Max security deposit at signature, as a fraction of price (Art. 618-4 = 5%). */
    public BigDecimal getDepotGarantieMaxPct() {
        return new BigDecimal("0.05");
    }

    /** Days to lift delivery reserves (Maroc contractual practice = 60). */
    public int getDelaiLeveeReservesJours() {
        return 60;
    }

    /**
     * Daily late-delivery penalty in MAD, applied per day of delay past dateLivraisonPrevue
     * (Art. 618-17 Loi 44-00 — montant contractuellement fixé, valeur par défaut 500 MAD/jour).
     * Override via {@code app.market.penalite-retard-journalier-mad}.
     */
    public BigDecimal getPenaliteRetardJournalierMad() {
        return penaliteRetardJournalierMad;
    }

    @Value("${app.market.penalite-retard-journalier-mad:500}")
    private BigDecimal penaliteRetardJournalierMad;

    public String getMarketCode() {
        return marketCode;
    }

    /** Human-readable legal basis for the active market (for document footers). */
    public String getBaseLegale() {
        return switch (marketCode) {
            case "FR" -> "Loi SRU / CCH L261-1";
            case "MA" -> "Loi n°44-00 du 3 octobre 2002 (VEFA Maroc)";
            default   -> "custom";
        };
    }
}
