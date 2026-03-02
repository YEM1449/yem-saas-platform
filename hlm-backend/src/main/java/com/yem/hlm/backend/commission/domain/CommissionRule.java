package com.yem.hlm.backend.commission.domain;

import com.yem.hlm.backend.project.domain.Project;
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
 * Defines a commission rate applicable for a tenant (optionally scoped to a project).
 * <p>
 * Rule lookup priority:
 * <ol>
 *   <li>Project-specific rule (project_id = contractProject AND date in range)</li>
 *   <li>Tenant-wide default (project_id IS NULL AND date in range)</li>
 * </ol>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "commission_rule",
        indexes = {
                @Index(name = "idx_cr_tenant_project",   columnList = "tenant_id,project_id"),
                @Index(name = "idx_cr_tenant_effective", columnList = "tenant_id,effective_from")
        }
)
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cr_tenant"))
    private Tenant tenant;

    /** Null means this is the tenant-wide default rule. */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id",
            foreignKey = @ForeignKey(name = "fk_cr_project"))
    private Project project;

    /** Commission as a percentage of agreedPrice (e.g. 2.50 = 2.5 %). */
    @Setter
    @Column(name = "rate_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal ratePercent;

    /** Optional fixed commission amount (added on top of the rate). */
    @Setter
    @Column(name = "fixed_amount", precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    @Setter
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means "until further notice". */
    @Setter
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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

    public CommissionRule(Tenant tenant, Project project,
                          BigDecimal ratePercent, BigDecimal fixedAmount,
                          LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.tenant        = tenant;
        this.project       = project;
        this.ratePercent   = ratePercent;
        this.fixedAmount   = fixedAmount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo   = effectiveTo;
    }
}
