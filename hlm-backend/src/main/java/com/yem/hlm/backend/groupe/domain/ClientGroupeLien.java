package com.yem.hlm.backend.groupe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a {@code contact} to a group-level "personne" cluster shared across the sociétés of
 * one owner (finding #005 — "link, don't merge").
 *
 * <p>Each société keeps its own contact row (a distinct responsable de traitement under
 * Loi 09-08); this row only records that the contact is the same physical person as the others
 * sharing its {@code groupePersonneId}, plus the consent that makes the cross-société link
 * lawful. It stores no PII — only opaque IDs — so reading it at group level never leaks one
 * société's personal data to another.
 */
@Entity
@Table(name = "client_groupe_lien")
public class ClientGroupeLien {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Cluster key: every contact that is the same person shares this value. */
    @Column(name = "groupe_personne_id", nullable = false)
    private UUID groupePersonneId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ClientGroupeLien() {}

    public ClientGroupeLien(UUID groupePersonneId, UUID contactId, UUID societeId,
                            boolean consentGiven, UUID linkedBy) {
        this.id = UUID.randomUUID();
        this.groupePersonneId = groupePersonneId;
        this.contactId = contactId;
        this.societeId = societeId;
        this.consentGiven = consentGiven;
        this.linkedBy = linkedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getGroupePersonneId() { return groupePersonneId; }
    public void setGroupePersonneId(UUID groupePersonneId) { this.groupePersonneId = groupePersonneId; }
    public UUID getContactId() { return contactId; }
    public UUID getSocieteId() { return societeId; }
    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }
    public UUID getLinkedBy() { return linkedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
