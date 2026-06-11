package com.yem.hlm.backend.legal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the Moroccan VAT rules (CGI) — Wave 12 P4. */
class TvaCalculatorTest {

    @Test
    @DisplayName("Logement social (≤100m², ≤250k, designated) → 0%")
    void social_zero() {
        assertThat(TvaCalculator.suggestTaux(new BigDecimal("200000"), new BigDecimal("80"), true))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Without the social designation, a small unit falls to the 10% bracket")
    void social_flagRequired() {
        assertThat(TvaCalculator.suggestTaux(new BigDecimal("200000"), new BigDecimal("80"), false))
                .isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("Logement moyen (≤150m², ≤700k) → 10%")
    void moyen_ten() {
        assertThat(TvaCalculator.suggestTaux(new BigDecimal("600000"), new BigDecimal("120"), false))
                .isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("All other cases → 20%")
    void normal_twenty() {
        assertThat(TvaCalculator.suggestTaux(new BigDecimal("1000000"), new BigDecimal("200"), false))
                .isEqualByComparingTo("0.20");
        // over the price cap even if small surface
        assertThat(TvaCalculator.suggestTaux(new BigDecimal("800000"), new BigDecimal("90"), false))
                .isEqualByComparingTo("0.20");
    }

    @Test
    @DisplayName("prix_ttc = prix_ht × (1 + taux)")
    void computesTtc() {
        assertThat(TvaCalculator.computePrixTtc(new BigDecimal("1000000"), new BigDecimal("0.20")))
                .isEqualByComparingTo("1200000.00");
        assertThat(TvaCalculator.computePrixTtc(new BigDecimal("500000"), BigDecimal.ZERO))
                .isEqualByComparingTo("500000.00");
        assertThat(TvaCalculator.computePrixTtc(null, new BigDecimal("0.20"))).isNull();
    }
}
