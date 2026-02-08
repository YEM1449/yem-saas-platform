package com.yem.hlm.backend.contact.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prospect_detail")
public class ProspectDetail {

    @Id
    @Column(name = "contact_id")
    private UUID contactId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prospect_detail_contact"))
    private Contact contact;

    @Setter
    @Column(name = "budget_min", precision = 19, scale = 2)
    private BigDecimal budgetMin;

    @Setter
    @Column(name = "budget_max", precision = 19, scale = 2)
    private BigDecimal budgetMax;

    @Setter
    @Column(name = "source", length = 80)
    private String source;

    @Setter
    @Column(name = "notes", length = 1000)
    private String notes;

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

    public ProspectDetail(Contact contact) {
        this.contact = contact;
        this.contactId = contact.getId();
    }
}
