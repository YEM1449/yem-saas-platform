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

    Optional<Reservation> findByTenant_IdAndId(UUID tenantId, UUID id);

    List<Reservation> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    /** True if property has an ACTIVE reservation in the given tenant. */
    boolean existsByTenant_IdAndPropertyIdAndStatus(UUID tenantId, UUID propertyId, ReservationStatus status);

    /** Find all ACTIVE reservations past their expiry date (for scheduler). */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expiryDate < :now")
    List<Reservation> findExpired(@Param("now") LocalDateTime now);

    /** Count active reservations for pipeline dashboard. */
    long countByTenant_IdAndStatus(UUID tenantId, ReservationStatus status);

    /** Count reservations expiring within the next N hours for pipeline alert. */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.tenant.id = :tenantId AND r.status = 'ACTIVE' AND r.expiryDate BETWEEN :now AND :horizon")
    long countExpiringBefore(@Param("tenantId") UUID tenantId,
                             @Param("now") LocalDateTime now,
                             @Param("horizon") LocalDateTime horizon);
}
