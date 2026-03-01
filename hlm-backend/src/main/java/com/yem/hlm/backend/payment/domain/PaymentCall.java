package com.yem.hlm.backend.payment.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Payment Call (Appel de Fonds) issued to the buyer for a specific tranche.
 * <p>
 * Lifecycle: {@link PaymentCallStatus#DRAFT} → {@link PaymentCallStatus#ISSUED}
 * → {@link PaymentCallStatus#OVERDUE} (by scheduler) → {@link PaymentCallStatus#CLOSED}
 * (when fully paid).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_call",
        indexes = {
                @Index(name = "idx_pc_tenant_status",   columnList = "tenant_id,status"),
                @Index(name = "idx_pc_tenant_tranche",  columnList = "tenant_id,tranche_id"),
                @Index(name = "idx_pc_tenant_due_date", columnList = "tenant_id,due_date")
        }
)
public class PaymentCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pc_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tranche_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pc_tranche"))
    private PaymentTranche tranche;

    /** Sequential call number within the tranche (typically 1). */
    @Column(name = "call_number", nullable = false)
    private int callNumber;

    @Column(name = "amount_due", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountDue;

    @Setter
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Setter
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentCallStatus status = PaymentCallStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = PaymentCallStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public PaymentCall(Tenant tenant, PaymentTranche tranche,
                       int callNumber, BigDecimal amountDue, LocalDate dueDate) {
        this.tenant     = tenant;
        this.tranche    = tranche;
        this.callNumber = callNumber;
        this.amountDue  = amountDue;
        this.dueDate    = dueDate;
        this.status     = PaymentCallStatus.DRAFT;
    }
}
