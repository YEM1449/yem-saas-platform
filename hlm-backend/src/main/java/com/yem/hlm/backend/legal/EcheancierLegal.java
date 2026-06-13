package com.yem.hlm.backend.legal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Legal call-for-funds schedule for VEFA sales (Maroc, Loi 44-00 Art. 618-17).
 * Percentages are the legal maxima per construction stage; they sum to 100%.
 * Reference: {@code docs/legal/loi-44-00-constantes.md}.
 */
public final class EcheancierLegal {

    public record EtapeLegale(int ordre, String code, String label, BigDecimal pct) {}

    public static final String BASE_LEGALE_MA = "Art. 618-17 Loi 44-00";

    /** The 7 legal stages (Maroc). Sum of pct = 100. */
    public static final List<EtapeLegale> MA = List.of(
            new EtapeLegale(1, "SIGNATURE_CONTRAT",         "Signature du contrat",        new BigDecimal("5")),
            new EtapeLegale(2, "ACHEVEMENT_FONDATIONS",     "Achèvement des fondations",   new BigDecimal("10")),
            new EtapeLegale(3, "ACHEVEMENT_PLANCHER_RDC",   "Achèvement plancher RDC",     new BigDecimal("15")),
            new EtapeLegale(4, "ACHEVEMENT_GROS_OEUVRE",    "Achèvement du gros œuvre",    new BigDecimal("20")),
            new EtapeLegale(5, "ACHEVEMENT_COUVERTURE",     "Achèvement de la couverture", new BigDecimal("20")),
            new EtapeLegale(6, "ACHEVEMENT_SECOND_OEUVRE",  "Achèvement du second œuvre",  new BigDecimal("20")),
            new EtapeLegale(7, "LIVRAISON_TITRE_PROPRIETE", "Livraison / titre de propriété", new BigDecimal("10"))
    );

    private EcheancierLegal() {}
}
