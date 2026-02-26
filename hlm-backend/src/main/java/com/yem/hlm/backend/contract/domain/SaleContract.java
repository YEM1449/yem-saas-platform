package com.yem.hlm.backend.contract.domain;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Sales Contract — the canonical anchor for "a sale occurred".
 * <p>
 * Business definition: <em>Sale = Contract SIGNED</em>. Deposits/reservations are pre-sale.
 * <p>
 * Status lifecycle: {@link SaleContractStatus#DRAFT} → {@link SaleContractStatus#SIGNED} or
 * {@link SaleContractStatus#CANCELED}.
 * <p>
 * DB integrity: A Postgres partial unique index ({@code uk_sc_property_signed}) prevents
 * more than one active SIGNED contract per {@code (tenant_id, property_id)}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "sale_contract",
        indexes = {
                @Index(name = "idx_sc_tenant_signed_at",          columnList = "tenant_id,signed_at"),
                @Index(name = "idx_sc_tenant_project_signed_at",  columnList = "tenant_id,project_id,signed_at"),
                @Index(name = "idx_sc_tenant_agent_signed_at",    columnList = "tenant_id,agent_id,signed_at"),
                @Index(name = "idx_sc_tenant_property",           columnList = "tenant_id,property_id")
        }
)
public class SaleContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_contract_tenant"))
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_contract_project"))
    private Project project;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_contract_property"))
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_contact_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_contract_contact"))
    private Contact buyerContact;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_contract_agent"))
    private User agent;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleContractStatus status;

    /** Agreed (negotiated) sale price — KPI anchor field. */
    @Setter
    @Column(name = "agreed_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal agreedPrice;

    /** Original list price — optional, enables discount KPIs. */
    @Setter
    @Column(name = "list_price", precision = 15, scale = 2)
    private BigDecimal listPrice;

    /** Optional reference to the confirmed deposit this contract was created from. */
    @Setter
    @Column(name = "source_deposit_id")
    private UUID sourceDepositId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Set when the contract transitions to SIGNED — the KPI timestamp. */
    @Setter
    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** Set when the contract transitions to CANCELED. */
    @Setter
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // ===== JPA lifecycle =====

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

    // ===== Constructor =====

    public SaleContract(Tenant tenant, Project project, Property property,
                        Contact buyerContact, User agent) {
        this.tenant = tenant;
        this.project = project;
        this.property = property;
        this.buyerContact = buyerContact;
        this.agent = agent;
        this.status = SaleContractStatus.DRAFT;
    }
}
