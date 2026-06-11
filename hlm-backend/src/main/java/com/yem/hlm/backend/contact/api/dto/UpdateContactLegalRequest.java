package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.SituationMatrimoniale;
import com.yem.hlm.backend.contact.domain.TypeAcquereur;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * VEFA legal identity update (Loi 44-00 / Office des Changes). All fields are nullable —
 * null means "do not change". Kept separate from {@code UpdateContactRequest} so the core
 * contact DTOs and their many call-sites stay stable.
 */
public record UpdateContactLegalRequest(
        LocalDate cinDateDelivrance,
        String cinAutorite,
        String passeportNumero,
        LocalDate passeportExpire,
        LocalDate dateNaissance,
        String lieuNaissance,
        SituationMatrimoniale situationMatrimoniale,
        String nationalite,
        String paysResidence,
        TypeAcquereur typeAcquereur,
        BigDecimal apportPersonnel
) {}
