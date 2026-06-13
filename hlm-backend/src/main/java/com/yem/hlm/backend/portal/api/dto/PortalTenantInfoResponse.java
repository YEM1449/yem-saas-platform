package com.yem.hlm.backend.portal.api.dto;

import java.time.LocalDate;

/**
 * Tenant branding + legal-identity info for the portal shell and the portal legal pages
 * (mentions légales / politique de confidentialité — findings #025/#026).
 *
 * <p>The data-protection fields ({@code dpoEmail}, {@code cndpNumber}, …) let the legal pages
 * display the société's <b>recorded</b> CNDP declaration and DPO contact instead of static
 * placeholders, so the declaration is evidenced on the consumer-facing surface once filed.
 * Any field may be {@code null} when the société hasn't recorded it yet; the UI degrades
 * gracefully.
 *
 * @param tenantName  display name (nom commercial → nom)
 * @param logoUrl     optional logo URL
 * @param legalName   raison sociale
 * @param rc          registre de commerce
 * @param ice         identifiant commun de l'entreprise
 * @param adresseSiege siège social
 * @param dpoEmail    contact DPO
 * @param dpoName     nom du DPO
 * @param cndpNumber  n° de récépissé de déclaration CNDP
 * @param cndpDeclarationDate date de la déclaration CNDP
 */
public record PortalTenantInfoResponse(
        String    tenantName,
        String    logoUrl,
        String    legalName,
        String    rc,
        String    ice,
        String    adresseSiege,
        String    dpoEmail,
        String    dpoName,
        String    cndpNumber,
        LocalDate cndpDeclarationDate
) {}
