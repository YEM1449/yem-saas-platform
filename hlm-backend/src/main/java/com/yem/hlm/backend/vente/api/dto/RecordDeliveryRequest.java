package com.yem.hlm.backend.vente.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for {@code POST /api/ventes/{id}/livraison}.
 * An empty/null {@code reserves} list means delivery without reserves (→ LIVRE_DEFINITIF);
 * otherwise each entry is a reserve description (→ LIVRE_AVEC_RESERVES).
 */
public record RecordDeliveryRequest(
        LocalDate dateLivraison,
        List<String> reserves
) {}
