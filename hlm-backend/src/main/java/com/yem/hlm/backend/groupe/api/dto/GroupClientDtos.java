package com.yem.hlm.backend.groupe.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs for the group client-identity feature (#005). PII is minimised: CIN is masked. */
public final class GroupClientDtos {

    private GroupClientDtos() {}

    /** One contact in the context of a group person, with its société. */
    public record ContactRef(
            UUID    contactId,
            UUID    societeId,
            String  societeNom,
            String  nomComplet,
            String  statut,
            boolean dejaLie
    ) {}

    /**
     * A potential link: the same masked CIN found in ≥2 of the owner's sociétés.
     * {@code cinMasque} shows only the last characters (e.g. "•••• 1234").
     */
    public record LinkCandidate(
            String           cinMasque,
            List<ContactRef> contacts
    ) {}

    /** An established group-person cluster. */
    public record GroupClient(
            UUID             groupePersonneId,
            boolean          consentGiven,
            Instant          lieLe,
            List<ContactRef> contacts
    ) {}

    /** Request to link several contacts as the same group person. */
    public record LinkClientsRequest(
            @NotEmpty(message = "Au moins deux contacts sont requis pour créer un lien.")
            List<UUID> contactIds,
            boolean    consentGiven
    ) {
        @AssertTrue(message = "Au moins deux contacts distincts sont requis.")
        public boolean isAtLeastTwoDistinct() {
            return contactIds != null && contactIds.stream().distinct().count() >= 2;
        }
    }
}
