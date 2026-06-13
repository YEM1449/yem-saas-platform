package com.yem.hlm.backend.vente.api.dto;

import com.yem.hlm.backend.contact.domain.SituationMatrimoniale;
import com.yem.hlm.backend.contact.domain.TypeAcquereur;
import com.yem.hlm.backend.vente.domain.RoleAcquereur;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update body for a co-buyer (POST/PUT /api/ventes/{id}/co-acquereurs). */
public record UpsertCoAcquereurRequest(
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @Size(max = 20) String cinNumero,
        LocalDate cinDateDelivrance,
        @Size(max = 30) String passeportNumero,
        LocalDate dateNaissance,
        @Size(max = 50) String nationalite,
        @Size(max = 50) String paysResidence,
        SituationMatrimoniale situationMatrimoniale,
        TypeAcquereur typeAcquereur,
        @Email @Size(max = 200) String email,
        @Size(max = 30) String telephone,
        RoleAcquereur roleAcquereur
) {}
