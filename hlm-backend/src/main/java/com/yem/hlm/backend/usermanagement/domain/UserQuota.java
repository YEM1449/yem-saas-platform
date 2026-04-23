package com.yem.hlm.backend.usermanagement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-user per-month CA and ventes-count targets, set by an ADMIN.
 * Takes precedence over société-wide caMensuelCible / ventesMensuelCible
 * when computing quota attainment on the home dashboard.
 */
@Entity
@Table(name = "user_quota",
       uniqueConstraints = @UniqueConstraint(name = "uq_user_quota_user_month",
               columnNames = {"societe_id", "user_id", "year_month"}))
public class UserQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** "YYYY-MM" e.g. "2026-04" */
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "ca_cible", precision = 15, scale = 2)
    private BigDecimal caCible;

    @Column(name = "ventes_count_cible")
    private Long ventesCountCible;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserQuota() {}

    public UserQuota(UUID societeId, UUID userId, String yearMonth,
                     BigDecimal caCible, Long ventesCountCible) {
        this.societeId       = societeId;
        this.userId          = userId;
        this.yearMonth       = yearMonth;
        this.caCible         = caCible;
        this.ventesCountCible = ventesCountCible;
        this.updatedAt       = LocalDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getSocieteId()           { return societeId; }
    public UUID getUserId()              { return userId; }
    public String getYearMonth()         { return yearMonth; }
    public BigDecimal getCaCible()       { return caCible; }
    public Long getVentesCountCible()    { return ventesCountCible; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }

    public void update(BigDecimal caCible, Long ventesCountCible) {
        this.caCible          = caCible;
        this.ventesCountCible = ventesCountCible;
        this.updatedAt        = LocalDateTime.now();
    }
}
