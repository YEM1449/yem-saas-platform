package com.yem.hlm.backend.payment.domain;

import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Payment Schedule groups the staged payment milestones for a signed sale contract.
 * <p>
 * One schedule per contract (enforced by unique constraint on {@code sale_contract_id}).
 * The actual milestones are the child {@link PaymentTranche} records.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment_schedule",
        indexes = {
                @Index(name = "idx_ps_tenant_contract", columnList = "tenant_id,sale_contract_id")
        }
)
public class PaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ps_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_contract_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_ps_sale_contract"))
    private SaleContract saleContract;

    @Setter
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("trancheOrder ASC")
    private List<PaymentTranche> tranches = new ArrayList<>();

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

    public PaymentSchedule(Tenant tenant, SaleContract saleContract, String notes) {
        this.tenant = tenant;
        this.saleContract = saleContract;
        this.notes = notes;
    }
}
