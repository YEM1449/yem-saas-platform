package com.yem.hlm.backend.deposit.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "deposit",
        indexes = {
                @Index(name = "idx_deposit_tenant_status", columnList = "societe_id,status"),
                @Index(name = "idx_deposit_tenant_contact", columnList = "societe_id,contact_id"),
                @Index(name = "idx_deposit_tenant_property", columnList = "societe_id,property_id"),
                @Index(name = "idx_deposit_tenant_agent", columnList = "societe_id,agent_id"),
                @Index(name = "idx_deposit_tenant_due_date", columnList = "societe_id,due_date")
        }
)
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_deposit_contact"))
    private Contact contact;

    /**
     * Property is not yet implemented in this codebase.
     * We store the id and enforce rules in service + DB indexes.
     */
    @Setter
    // DB column is nullable for legacy rows; MVP enforces NOT NULL at service + DTO level.
    @Column(name = "property_id")
    private UUID propertyId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false, foreignKey = @ForeignKey(name = "fk_deposit_agent"))
    private User agent;

    @Setter
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Setter
    // Currency is NOT NULL at runtime; a DB NOT NULL constraint is added in changeset 007.
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Setter
    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Setter
    @Column(name = "reference", length = 50, nullable = false)
    private String reference;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepositStatus status;

    @Setter
    @Column(name = "notes")
    private String notes;

    @Setter
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Setter
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Setter
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.currency == null) this.currency = "MAD";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Deposit(UUID societeId, Contact contact, User agent) {
        this.societeId = societeId;
        this.contact = contact;
        this.agent = agent;
    }
}
