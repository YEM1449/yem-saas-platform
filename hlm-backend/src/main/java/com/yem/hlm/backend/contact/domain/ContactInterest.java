package com.yem.hlm.backend.contact.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "contact_interest",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contact_interest_tenant_contact_property",
                        columnNames = {"societe_id", "contact_id", "property_id"}
                )
        },
        indexes = {
                @Index(name = "idx_ci_tenant_contact", columnList = "societe_id,contact_id"),
                @Index(name = "idx_ci_tenant_property", columnList = "societe_id,property_id")
        }
)
public class ContactInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_status", length = 32)
    private InterestStatus interestStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ContactInterest(UUID societeId, UUID contactId, UUID propertyId, InterestStatus interestStatus) {
        this.societeId      = societeId;
        this.contactId      = contactId;
        this.propertyId     = propertyId;
        this.interestStatus = interestStatus;
    }
}
