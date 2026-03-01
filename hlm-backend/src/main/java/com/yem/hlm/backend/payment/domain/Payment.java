package com.yem.hlm.backend.payment.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records an actual cash-in event against a {@link PaymentCall}.
 * <p>
 * Multiple partial payments per call are supported. When the sum of
 * {@code amountReceived} across all payments for a call reaches
 * {@code call.amountDue}, the call transitions to CLOSED and the tranche to PAID.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment",
        indexes = {
                @Index(name = "idx_payment_tenant_call", columnList = "tenant_id,payment_call_id")
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_call_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_call"))
    private PaymentCall paymentCall;

    @Column(name = "amount_received", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountReceived;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 30, nullable = false)
    private PaymentMethod method;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Payment(Tenant tenant, PaymentCall paymentCall,
                   BigDecimal amountReceived, LocalDate receivedAt,
                   PaymentMethod method, String reference, String notes) {
        this.tenant         = tenant;
        this.paymentCall    = paymentCall;
        this.amountReceived = amountReceived;
        this.receivedAt     = receivedAt;
        this.method         = method;
        this.reference      = reference;
        this.notes          = notes;
    }
}
