package com.yem.hlm.backend.contact.domain;

/**
 * ContactType = "profil" du contact (prospect vs client) au niveau CRM.
 *
 * Important:
 * - Le statut de workflow (ContactStatus) est séparé.
 * - TEMP_CLIENT représente la période de 7 jours où un prospect a posé un acompte
 *   mais n'est pas encore confirmé (dépôt non validé).
 */
public enum ContactType {
    PROSPECT,
    TEMP_CLIENT,
    CLIENT
}
