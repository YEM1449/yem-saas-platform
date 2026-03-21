package com.yem.hlm.backend.contact.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "contact",
        indexes = {
                @Index(name = "idx_contact_tenant_status", columnList = "societe_id,status"),
                @Index(name = "idx_contact_tenant_last_name", columnList = "societe_id,last_name"),
                @Index(name = "idx_contact_tenant_created_at", columnList = "societe_id,created_at"),
                @Index(name = "idx_contact_tenant_type", columnList = "societe_id,contact_type")
        }
)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    /**
     * Legacy-but-still-enforced NOT NULL column from changeset 003.
     * We keep it in sync with (firstName, lastName).
     */
    @Setter
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /**
     * High-level profile of the contact.
     * Stored in legacy column contact_type (NOT NULL).
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

    /**
     * Workflow status for the contact lifecycle.
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ContactStatus status;

    @Setter
    @Column(name = "qualified", nullable = false)
    private boolean qualified;

    /**
     * When the contact is TEMP_CLIENT, the reservation ends at this instant.
     */
    @Setter
    @Column(name = "temp_client_until")
    private LocalDateTime tempClientUntil;

    @Setter
    @Column(name = "lost_reason", length = 255)
    private String lostReason;

    @Setter
    @Column(name = "created_by")
    private UUID createdBy;

    @Setter
    @Column(name = "updated_by")
    private UUID updatedBy;

    // Common identity / contact fields
    @Setter
    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Setter
    @Column(name = "phone", length = 30)
    private String phone;

    @Setter
    @Column(name = "email", length = 160)
    private String email;

    @Setter
    @Column(name = "national_id", length = 100)
    private String nationalId;

    @Setter
    @Column(name = "address", length = 500)
    private String address;

    @Setter
    @Column(name = "notes", length = 2000)
    private String notes;

    @Setter
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    // ===== GDPR / Law 09-08 consent fields =====

    /** Whether the contact has given explicit consent to data processing. */
    @Setter
    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    /** Timestamp when consent was recorded. Set automatically when consentGiven transitions to true. */
    @Setter
    @Column(name = "consent_date")
    private Instant consentDate;

    /** How consent was collected (CRM_ENTRY, PORTAL, PAPER, EMAIL). */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_method", length = 100)
    private ConsentMethod consentMethod;

    /** Legal basis for processing this contact's personal data. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_basis", length = 100)
    private ProcessingBasis processingBasis;

    /**
     * Contact-level data retention override (in days).
     * When NULL, the tenant default (app.gdpr.default-retention-days) applies.
     */
    @Setter
    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    /** Set when this contact has been fully anonymized (GDPR Art. 17 erasure). */
    @Setter
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        syncFullName();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        syncFullName();
    }

    public Contact(UUID societeId, UUID actorUserId, String firstName, String lastName) {
        this.societeId = societeId;
        this.firstName = firstName;
        this.lastName = lastName;

        this.contactType = ContactType.PROSPECT;
        this.status = ContactStatus.PROSPECT;
        this.qualified = false;
        this.deleted = false;
        this.consentGiven = false;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
        syncFullName();
    }

    public void markUpdatedBy(UUID actorUserId) {
        this.updatedBy = actorUserId;
    }

    public void syncFullName() {
        String fn = (firstName == null) ? "" : firstName.trim();
        String ln = (lastName == null) ? "" : lastName.trim();
        String combined = (fn + " " + ln).trim();
        this.fullName = combined.isEmpty() ? "Unknown" : combined;
    }
}
