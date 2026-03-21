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

    Optional<Deposit> findBySocieteIdAndId(UUID societeId, UUID id);

    boolean existsBySocieteIdAndContact_IdAndPropertyId(UUID societeId, UUID contactId, UUID propertyId);

    boolean existsBySocieteIdAndPropertyIdAndStatusIn(UUID societeId, UUID propertyId, List<DepositStatus> statuses);

    /** Single-status variant — used by the default helper below. */
    boolean existsBySocieteIdAndPropertyIdAndStatus(UUID societeId, UUID propertyId, DepositStatus status);

    /**
     * True if a CONFIRMED (active) deposit exists for the given property within the société.
     */
    default boolean existsActiveConfirmedDepositForProperty(UUID societeId, UUID propertyId) {
        return existsBySocieteIdAndPropertyIdAndStatus(societeId, propertyId, DepositStatus.CONFIRMED);
    }

    List<Deposit> findAllBySocieteIdAndContact_IdAndStatus(UUID societeId, UUID contactId, DepositStatus status);

    List<Deposit> findAllBySocieteIdAndStatusAndDueDateBefore(UUID societeId, DepositStatus status, LocalDateTime before);

    // For scheduler workflows (cross-société, runs without SocieteContext)
    List<Deposit> findAllByStatusAndDueDateBefore(DepositStatus status, LocalDateTime before);

    List<Deposit> findAllByStatusAndDueDateBetween(DepositStatus status, LocalDateTime from, LocalDateTime to);

    /**
     * Loads a deposit with JOIN FETCH on contact and agent to avoid N+1.
     * Used by {@link com.yem.hlm.backend.deposit.service.pdf.ReservationDocumentService}
     * when building the PDF model.
     */
    @Query("""
            SELECT d FROM Deposit d
            JOIN FETCH d.contact
            JOIN FETCH d.agent
            WHERE d.societeId = :societeId AND d.id = :id
            """)
    Optional<Deposit> findForPdf(
            @Param("societeId") UUID societeId,
            @Param("id") UUID id
    );

    @Query("""
            select d from Deposit d
            where d.societeId = :societeId
              and (:status is null or d.status = :status)
              and (:agentId is null or d.agent.id = :agentId)
              and (:contactId is null or d.contact.id = :contactId)
              and (:propertyId is null or d.propertyId = :propertyId)
              and (cast(:from as LocalDateTime) is null or d.createdAt >= :from)
              and (cast(:to as LocalDateTime) is null or d.createdAt <= :to)
            order by d.createdAt desc
            """)
    List<Deposit> report(
            @Param("societeId") UUID societeId,
            @Param("status") DepositStatus status,
            @Param("agentId") UUID agentId,
            @Param("contactId") UUID contactId,
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select d from Deposit d
            where d.societeId = :societeId
              and d.status = 'PENDING'
              and d.dueDate is not null
              and d.dueDate >= :from
              and d.dueDate <= :to
            """)
    List<Deposit> findDueSoon(
            @Param("societeId") UUID societeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // =========================================================================
    // Dashboard aggregate queries (no entity hydration — DTO projections only)
    // =========================================================================

    @Query("""
            SELECT COUNT(d), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.societeId   = :societeId
              AND d.status      = 'CONFIRMED'
              AND d.confirmedAt >= :from
              AND d.confirmedAt <= :to
              AND (:agentId     IS NULL OR d.agent.id = :agentId)
            """)
    List<Object[]> depositTotals(
            @Param("societeId") UUID societeId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    @Query("""
            SELECT cast(d.confirmedAt as LocalDate), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.societeId   = :societeId
              AND d.status      = 'CONFIRMED'
              AND d.confirmedAt >= :from
              AND d.confirmedAt <= :to
              AND (:agentId     IS NULL OR d.agent.id = :agentId)
            GROUP BY cast(d.confirmedAt as LocalDate)
            ORDER BY cast(d.confirmedAt as LocalDate)
            """)
    List<Object[]> depositsAmountByDay(
            @Param("societeId") UUID societeId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("agentId")  UUID agentId
    );

    @Query("""
            SELECT COUNT(d), COALESCE(SUM(d.amount), 0)
            FROM Deposit d
            WHERE d.societeId = :societeId
              AND d.status IN ('PENDING', 'CONFIRMED')
              AND (:agentId IS NULL OR d.agent.id = :agentId)
            """)
    List<Object[]> activeReservationTotals(
            @Param("societeId") UUID societeId,
            @Param("agentId")  UUID agentId
    );

    @Query("""
            SELECT d.depositDate
            FROM Deposit d
            WHERE d.societeId = :societeId
              AND d.status IN ('PENDING', 'CONFIRMED')
              AND (:agentId IS NULL OR d.agent.id = :agentId)
            """)
    List<java.time.LocalDate> activeReservationDepositDates(
            @Param("societeId") UUID societeId,
            @Param("agentId")  UUID agentId
    );
}
