package com.yem.hlm.backend.payments.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Actual cash-in record linked to a {@link PaymentScheduleItem}.
 * Supports partial payments — multiple rows per schedule item are allowed.
 * Once created, payment records are immutable (no updates allowed).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "schedule_payment",
        indexes = {
                @Index(name = "idx_spay_tenant_paid_at", columnList = "societe_id,paid_at"),
                @Index(name = "idx_spay_tenant_item",    columnList = "societe_id,schedule_item_id")
        }
)
public class SchedulePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "schedule_item_id", nullable = false)
    private UUID scheduleItemId;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    /** Payment channel: BANK_TRANSFER, CASH, CHEQUE, CARD, or null if unspecified. */
    @Column(name = "channel", length = 30)
    private String channel;

    /** External reference (bank transfer ref, cheque number, etc.). */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public SchedulePayment(UUID societeId, UUID scheduleItemId, UUID createdBy,
                           BigDecimal amountPaid, LocalDateTime paidAt,
                           String channel, String paymentReference, String notes) {
        this.societeId        = societeId;
        this.scheduleItemId   = scheduleItemId;
        this.createdBy        = createdBy;
        this.amountPaid       = amountPaid;
        this.paidAt           = paidAt;
        this.channel          = channel;
        this.paymentReference = paymentReference;
        this.notes            = notes;
    }
}
