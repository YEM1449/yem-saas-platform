package com.yem.hlm.backend.contact.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "contact",
        indexes = {
                @Index(name = "idx_contact_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "idx_contact_tenant_last_name", columnList = "tenant_id,last_name"),
                @Index(name = "idx_contact_tenant_created_at", columnList = "tenant_id,created_at"),
                @Index(name = "idx_contact_tenant_type", columnList = "tenant_id,contact_type")
        }
)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contact_tenant"))
    private Tenant tenant;

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

    public Contact(Tenant tenant, UUID actorUserId, String firstName, String lastName) {
        this.tenant = tenant;
        this.firstName = firstName;
        this.lastName = lastName;

        this.contactType = ContactType.PROSPECT;
        this.status = ContactStatus.PROSPECT;
        this.qualified = false;
        this.deleted = false;
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
