package com.yem.hlm.backend.reservation.repo;

import com.yem.hlm.backend.reservation.domain.Reservation;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findBySocieteIdAndId(UUID societeId, UUID id);

    List<Reservation> findAllBySocieteIdOrderByCreatedAtDesc(UUID societeId);

    /** Filter reservations by contact — used by the ProspectDetail page. */
    List<Reservation> findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(UUID societeId, UUID contactId);

    /** True if property has an ACTIVE reservation in the given société. */
    boolean existsBySocieteIdAndPropertyIdAndStatus(UUID societeId, UUID propertyId, ReservationStatus status);

    /** Find all ACTIVE reservations past their expiry date (for scheduler). */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expiryDate < :now")
    List<Reservation> findExpired(@Param("now") LocalDateTime now);

    /** Count active reservations for pipeline dashboard. */
    long countBySocieteIdAndStatus(UUID societeId, ReservationStatus status);

    /** Count reservations expiring within the next N hours for pipeline alert. */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.societeId = :societeId AND r.status = 'ACTIVE' AND r.expiryDate BETWEEN :now AND :horizon")
    long countExpiringBefore(@Param("societeId") UUID societeId,
                             @Param("now") LocalDateTime now,
                             @Param("horizon") LocalDateTime horizon);

    /**
     * Count reservations created in [from, to). Used by the home dashboard
     * conversion-rate KPI (ventes 30d / reservations 30d).
     */
    @Query("""
            SELECT COUNT(r)
            FROM Reservation r
            WHERE r.societeId = :societeId
              AND r.createdAt >= :from
              AND r.createdAt <  :to
            """)
    long countCreatedInPeriod(@Param("societeId") UUID societeId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
