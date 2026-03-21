package com.yem.hlm.backend.reservation.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A lightweight "intent to buy" that reserves a property for a prospect
 * before a formal financial Deposit is created.
 * <p>
 * Lifecycle: ACTIVE → EXPIRED | CANCELLED | CONVERTED_TO_DEPOSIT
 * <p>
 * When ACTIVE, the linked property has status RESERVED.
 * On expiry/cancellation the property is released back to ACTIVE.
 * Conversion produces a new Deposit and transitions this reservation to CONVERTED_TO_DEPOSIT.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "property_reservation",
        indexes = {
                @Index(name = "idx_preser_tenant_status",   columnList = "societe_id,status"),
                @Index(name = "idx_preser_tenant_property", columnList = "societe_id,property_id"),
                @Index(name = "idx_preser_tenant_contact",  columnList = "societe_id,contact_id"),
                @Index(name = "idx_preser_expiry_date",     columnList = "expiry_date")
        }
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_preser_contact"))
    private Contact contact;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reserved_by_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_preser_user"))
    private User reservedByUser;

    @Setter
    @Column(name = "reservation_price", precision = 12, scale = 2)
    private BigDecimal reservationPrice;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Setter
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    @Setter
    @Column(name = "notes")
    private String notes;

    /** Set when this reservation is converted to a deposit. */
    @Setter
    @Column(name = "converted_deposit_id")
    private UUID convertedDepositId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Reservation(UUID societeId, Contact contact, UUID propertyId, User reservedByUser) {
        this.societeId       = societeId;
        this.contact         = contact;
        this.propertyId      = propertyId;
        this.reservedByUser  = reservedByUser;
        this.reservationDate = LocalDate.now();
        this.status          = ReservationStatus.ACTIVE;
    }
}
