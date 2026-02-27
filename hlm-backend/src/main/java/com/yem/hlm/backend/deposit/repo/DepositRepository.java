package com.yem.hlm.backend.deposit.repo;

import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    Optional<Deposit> findByTenant_IdAndId(UUID tenantId, UUID id);

    boolean existsByTenant_IdAndContact_IdAndPropertyId(UUID tenantId, UUID contactId, UUID propertyId);

    boolean existsByTenant_IdAndPropertyIdAndStatusIn(UUID tenantId, UUID propertyId, List<DepositStatus> statuses);

    /** Single-status variant — used by the default helper below. */
    boolean existsByTenant_IdAndPropertyIdAndStatus(UUID tenantId, UUID propertyId, DepositStatus status);

    /**
     * True if a CONFIRMED (active) deposit exists for the given property within the tenant.
     * <p>
     * Used by {@code SaleContractService.cancel()} to decide whether to revert the property
     * to RESERVED (active reservation remains) or AVAILABLE (PropertyStatus.ACTIVE).
     */
    default boolean existsActiveConfirmedDepositForProperty(UUID tenantId, UUID propertyId) {
        return existsByTenant_IdAndPropertyIdAndStatus(tenantId, propertyId, DepositStatus.CONFIRMED);
    }

    List<Deposit> findAllByTenant_IdAndContact_IdAndStatus(UUID tenantId, UUID contactId, DepositStatus status);

    List<Deposit> findAllByTenant_IdAndStatusAndDueDateBefore(UUID tenantId, DepositStatus status, LocalDateTime before);

    // For scheduler workflows (cross-tenant, runs without TenantContext)
    List<Deposit> findAllByStatusAndDueDateBefore(DepositStatus status, LocalDateTime before);

    List<Deposit> findAllByStatusAndDueDateBetween(DepositStatus status, LocalDateTime from, LocalDateTime to);


    /**
     * Loads a deposit with JOIN FETCH on tenant, contact, and agent to avoid N+1.
     * Used by {@link com.yem.hlm.backend.deposit.service.pdf.ReservationDocumentService}
     * when building the PDF model.
     */
    @Query("""
            SELECT d FROM Deposit d
            JOIN FETCH d.tenant
            JOIN FETCH d.contact
            JOIN FETCH d.agent
            WHERE d.tenant.id = :tenantId AND d.id = :id
            """)
    Optional<Deposit> findForPdf(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );

    @Query("""
            select d from Deposit d
            where d.tenant.id = :tenantId
              and (:status is null or d.status = :status)
              and (:agentId is null or d.agent.id = :agentId)
              and (:contactId is null or d.contact.id = :contactId)
              and (:propertyId is null or d.propertyId = :propertyId)
              and (cast(:from as LocalDateTime) is null or d.createdAt >= :from)
              and (cast(:to as LocalDateTime) is null or d.createdAt <= :to)
            order by d.createdAt desc
            """)
    List<Deposit> report(
            @Param("tenantId") UUID tenantId,
            @Param("status") DepositStatus status,
            @Param("agentId") UUID agentId,
            @Param("contactId") UUID contactId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select d from Deposit d
            where d.tenant.id = :tenantId
              and d.status = 'PENDING'
              and d.dueDate is not null
              and d.dueDate >= :from
              and d.dueDate <= :to
            """)
    List<Deposit> findDueSoon(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // =========================================================================
    // Dashboard aggregate queries (no entity hydration — DTO projections only)
    // =========================================================================

    /**
     * Totals for CONFIRMED deposits in the given date range (filtered by confirmedAt).
     * Returns one Object[] row: [count(Long), sum(BigDecimal)].
     */
    @Query("""
            SELECT COUNT(d), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.tenant.id   = :tenantId
              AND d.status      = 'CONFIRMED'
              AND d.confirmedAt >= :from
              AND d.confirmedAt <= :to
              AND (:agentId     IS NULL OR d.agent.id = :agentId)
            """)
    List<Object[]> depositTotals(
            @Param("tenantId") UUID tenantId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    /**
     * Daily deposit amounts (trend chart). Groups CONFIRMED deposits by calendar day of confirmedAt.
     * Returns rows: [date(LocalDate), amount(BigDecimal)], ordered by date ASC.
     */
    @Query("""
            SELECT cast(d.confirmedAt as LocalDate), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.tenant.id   = :tenantId
              AND d.status      = 'CONFIRMED'
              AND d.confirmedAt >= :from
              AND d.confirmedAt <= :to
              AND (:agentId     IS NULL OR d.agent.id = :agentId)
            GROUP BY cast(d.confirmedAt as LocalDate)
            ORDER BY cast(d.confirmedAt as LocalDate)
            """)
    List<Object[]> depositsAmountByDay(
            @Param("tenantId") UUID tenantId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    /**
     * Snapshot of currently active reservations (PENDING + CONFIRMED), not date-filtered.
     * Used by the commercial dashboard for real-time pipeline view.
     *
     * <p>Returns one Object[] row:
     * {@code [count(Long), sum(BigDecimal), depositDates(List<LocalDate>)]} — actually
     * three separate scalar projections: count, totalAmount, and a list of depositDate values
     * is NOT returned here; see {@link #activeReservationDepositDates} for age calculation.
     *
     * <p>Returns one Object[] row: {@code [count(Long), totalAmount(BigDecimal)]}.
     */
    @Query("""
            SELECT COUNT(d), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.tenant.id = :tenantId
              AND d.status IN ('PENDING', 'CONFIRMED')
              AND (:agentId IS NULL OR d.agent.id = :agentId)
            """)
    List<Object[]> activeReservationTotals(
            @Param("tenantId") UUID tenantId,
            @Param("agentId")  UUID agentId
    );

    /**
     * Returns deposit dates of currently active reservations for average-age calculation.
     * Only fetches one date column — no entity hydration.
     */
    @Query("""
            SELECT d.depositDate
            FROM Deposit d
            WHERE d.tenant.id = :tenantId
              AND d.status IN ('PENDING', 'CONFIRMED')
              AND (:agentId IS NULL OR d.agent.id = :agentId)
            """)
    List<java.time.LocalDate> activeReservationDepositDates(
            @Param("tenantId") UUID tenantId,
            @Param("agentId")  UUID agentId
    );
}
