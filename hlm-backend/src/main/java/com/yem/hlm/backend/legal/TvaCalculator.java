package com.yem.hlm.backend.legal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VAT (TVA) helper for Moroccan real estate (Code Général des Impôts).
 * <ul>
 *   <li>Logement social : surface ≤ 100 m² ET prix ≤ 250 000 MAD ET désigné social → 0 %</li>
 *   <li>Logement moyen  : surface ≤ 150 m² ET prix ≤ 700 000 MAD → 10 %</li>
 *   <li>Taux normal     : tous les autres cas → 20 %</li>
 * </ul>
 * {@code prix_ttc} is always computed ({@code prix_ht × (1 + taux)}) — never stored.
 */
public final class TvaCalculator {

    public static final BigDecimal TAUX_SOCIAL = BigDecimal.ZERO;
    public static final BigDecimal TAUX_MOYEN  = new BigDecimal("0.10");
    public static final BigDecimal TAUX_NORMAL = new BigDecimal("0.20");

    private static final BigDecimal SOCIAL_SURFACE_MAX = new BigDecimal("100");
    private static final BigDecimal SOCIAL_PRIX_MAX    = new BigDecimal("250000");
    private static final BigDecimal MOYEN_SURFACE_MAX  = new BigDecimal("150");
    private static final BigDecimal MOYEN_PRIX_MAX     = new BigDecimal("700000");

    private TvaCalculator() {}

    /** Suggests the legal VAT rate from surface, HT price and the social designation. */
    public static BigDecimal suggestTaux(BigDecimal prixHt, BigDecimal surface, boolean logementSocial) {
        if (prixHt == null || surface == null) return TAUX_NORMAL;
        if (logementSocial
                && surface.compareTo(SOCIAL_SURFACE_MAX) <= 0
                && prixHt.compareTo(SOCIAL_PRIX_MAX) <= 0) {
            return TAUX_SOCIAL;
        }
        if (surface.compareTo(MOYEN_SURFACE_MAX) <= 0 && prixHt.compareTo(MOYEN_PRIX_MAX) <= 0) {
            return TAUX_MOYEN;
        }
        return TAUX_NORMAL;
    }

    /** prix_ttc = prix_ht × (1 + taux), rounded to 2 decimals. Null-safe (returns null if HT null). */
    public static BigDecimal computePrixTtc(BigDecimal prixHt, BigDecimal tauxTva) {
        if (prixHt == null) return null;
        BigDecimal taux = tauxTva != null ? tauxTva : BigDecimal.ZERO;
        return prixHt.multiply(BigDecimal.ONE.add(taux)).setScale(2, RoundingMode.HALF_UP);
    }
}
