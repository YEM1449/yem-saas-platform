package com.yem.hlm.backend.legal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
