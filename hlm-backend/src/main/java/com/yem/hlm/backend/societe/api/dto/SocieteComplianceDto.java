package com.yem.hlm.backend.societe.api.dto;

import java.util.List;

/**
 * RGPD compliance snapshot for a company.
 * Score is 0–100; {@code missingFields} lists labels of what is missing.
 */
public record SocieteComplianceDto(
        int            score,
        boolean        hasNom,
        boolean        hasEmailDpo,
        boolean        hasAdresse,
        boolean        hasRegistreNumber,   // numeroCndp or numeroCnil
        boolean        hasDpoNom,
        boolean        hasBaseJuridique,
        List<String>   missingFields
) {
    public static SocieteComplianceDto from(
            boolean hasNom,
            boolean hasEmailDpo,
            boolean hasAdresse,
            boolean hasRegistreNumber,
            boolean hasDpoNom,
            boolean hasBaseJuridique
    ) {
        int score = 0;
        var missing = new java.util.ArrayList<String>();

        if (hasNom)             score += 20; else missing.add("nom");
        if (hasEmailDpo)        score += 20; else missing.add("emailDpo");
        if (hasAdresse)         score += 10; else missing.add("adresseSiege");
        if (hasRegistreNumber)  score += 30; else missing.add("numeroCndp / numeroCnil");
        if (hasDpoNom)          score += 10; else missing.add("dpoNom");
        if (hasBaseJuridique)   score += 10; else missing.add("baseJuridiqueDefaut");

        return new SocieteComplianceDto(score, hasNom, hasEmailDpo, hasAdresse,
                hasRegistreNumber, hasDpoNom, hasBaseJuridique,
                List.copyOf(missing));
    }
}
