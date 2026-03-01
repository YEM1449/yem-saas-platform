package com.yem.hlm.backend.payments.domain;

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
 * One installment / call-for-funds in a contract's payment schedule.
 *
 * <p>Status lifecycle:
 * <ul>
 *   <li>DRAFT → ISSUED (explicit {@code issue()} action)</li>
 *   <li>ISSUED → SENT (via {@code send()} action with outbox dispatch)</li>
 *   <li>ISSUED|SENT → OVERDUE (set daily by {@link com.yem.hlm.backend.payments.service.ReminderService})</li>
 *   <li>ISSUED|SENT|OVERDUE → PAID (when sum(payments) >= amount)</li>
 *   <li>DRAFT|ISSUED|SENT|OVERDUE → CANCELED</li>
 * </ul>
 *
 * <p>Remaining amount and PAID transition are managed by
 * {@link com.yem.hlm.backend.payments.service.CallForFundsWorkflowService}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_schedule_item",
        indexes = {
                @Index(name = "idx_psi_tenant_contract",    columnList = "tenant_id,contract_id"),
                @Index(name = "idx_psi_tenant_project_due", columnList = "tenant_id,project_id,due_date"),
                @Index(name = "idx_psi_tenant_due_status",  columnList = "tenant_id,due_date,status")
        }
)
public class PaymentScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_psi_tenant"))
    private Tenant tenant;

    /** FK to sale_contract — denormalized for fast queries. */
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    /** Denormalized from contract.project_id for dashboard aggregate queries. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Denormalized from contract.property_id. */
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    /** 1-based display order within the contract's schedule. */
    @Setter
    @Column(name = "sequence", nullable = false)
    private int sequence;

    /** Human-readable label, e.g. "Acompte 1 - 10 % signature". */
    @Setter
    @Column(name = "label", nullable = false, length = 200)
    private String label;

    /** Planned amount due for this installment. */
    @Setter
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Date by which the payment is expected. */
    @Setter
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentScheduleStatus status;

    @Setter
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Setter
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Setter
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Setter
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = PaymentScheduleStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public PaymentScheduleItem(Tenant tenant, UUID contractId, UUID projectId, UUID propertyId,
                               UUID createdBy, int sequence, String label,
                               BigDecimal amount, LocalDate dueDate, String notes) {
        this.tenant      = tenant;
        this.contractId  = contractId;
        this.projectId   = projectId;
        this.propertyId  = propertyId;
        this.createdBy   = createdBy;
        this.sequence    = sequence;
        this.label       = label;
        this.amount      = amount;
        this.dueDate     = dueDate;
        this.notes       = notes;
        this.status      = PaymentScheduleStatus.DRAFT;
    }
}
