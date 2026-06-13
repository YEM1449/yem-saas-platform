package com.yem.hlm.backend.contact.api.dto;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.SituationMatrimoniale;
import com.yem.hlm.backend.contact.domain.TypeAcquereur;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** VEFA legal identity view of a contact (CIN is the existing nationalId). */
public record ContactLegalResponse(
        UUID contactId,
        String nationalId,
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
) {
    public static ContactLegalResponse from(Contact c) {
        return new ContactLegalResponse(
                c.getId(), c.getNationalId(), c.getCinDateDelivrance(), c.getCinAutorite(),
                c.getPasseportNumero(), c.getPasseportExpire(), c.getDateNaissance(),
                c.getLieuNaissance(), c.getSituationMatrimoniale(), c.getNationalite(),
                c.getPaysResidence(), c.getTypeAcquereur(), c.getApportPersonnel());
    }
}
