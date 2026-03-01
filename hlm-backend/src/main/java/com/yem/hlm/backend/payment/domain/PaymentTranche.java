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
 * A single staged payment milestone within a {@link PaymentSchedule}.
 * <p>
 * Example: "Fondations achevées — 30 % — 450 000 MAD — due 2026-06-01".
 * <p>
 * Status lifecycle: {@link TrancheStatus#PLANNED} → {@link TrancheStatus#ISSUED}
 * (when a {@link PaymentCall} is issued) → {@link TrancheStatus#PARTIALLY_PAID}
 * / {@link TrancheStatus#PAID} / {@link TrancheStatus#OVERDUE}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_tranche",
        indexes = {
                @Index(name = "idx_pt_tenant_schedule", columnList = "tenant_id,payment_schedule_id"),
                @Index(name = "idx_pt_tenant_status",   columnList = "tenant_id,status")
        }
)
public class PaymentTranche {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pt_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_schedule_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pt_payment_schedule"))
    private PaymentSchedule schedule;

    /** 1-based display order within the schedule. */
    @Column(name = "tranche_order", nullable = false)
    private int trancheOrder;

    /** Human-readable label, e.g. "Fondations achevées". */
    @Setter
    @Column(name = "label", length = 200, nullable = false)
    private String label;

    /** Percentage of the total agreed price (0.01 – 100.00). */
    @Setter
    @Column(name = "percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal percentage;

    /** Absolute amount in MAD (= agreedPrice * percentage / 100 at creation time). */
    @Setter
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    /** Optional calendar due date for this tranche. */
    @Setter
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Optional event-based trigger (e.g. "Achèvement des fondations"). */
    @Setter
    @Column(name = "trigger_condition", length = 500)
    private String triggerCondition;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TrancheStatus status = TrancheStatus.PLANNED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = TrancheStatus.PLANNED;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public PaymentTranche(Tenant tenant, PaymentSchedule schedule,
                          int trancheOrder, String label,
                          BigDecimal percentage, BigDecimal amount,
                          LocalDate dueDate, String triggerCondition) {
        this.tenant           = tenant;
        this.schedule         = schedule;
        this.trancheOrder     = trancheOrder;
        this.label            = label;
        this.percentage       = percentage;
        this.amount           = amount;
        this.dueDate          = dueDate;
        this.triggerCondition = triggerCondition;
        this.status           = TrancheStatus.PLANNED;
    }
}
