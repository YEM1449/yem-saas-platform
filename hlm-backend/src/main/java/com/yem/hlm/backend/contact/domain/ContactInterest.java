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
        name = "contact_interest",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contact_interest_tenant_contact_property",
                        columnNames = {"tenant_id", "contact_id", "property_id"}
                )
        },
        indexes = {
                @Index(name = "idx_ci_tenant_contact", columnList = "tenant_id,contact_id"),
                @Index(name = "idx_ci_tenant_property", columnList = "tenant_id,property_id")
        }
)
public class ContactInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ci_tenant"))
    private Tenant tenant;

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

    public ContactInterest(Tenant tenant, UUID contactId, UUID propertyId, InterestStatus interestStatus) {
        this.tenant = tenant;
        this.contactId = contactId;
        this.propertyId = propertyId;
        this.interestStatus = interestStatus;
    }
}
