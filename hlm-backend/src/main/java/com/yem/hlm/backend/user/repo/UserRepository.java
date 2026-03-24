package com.yem.hlm.backend.user.repo;

import com.yem.hlm.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /**
     * Safe alternative to findByEmail for deployments where pre-migration data
     * may have left duplicate email rows. Returns the first matching user,
     * avoiding NonUniqueResultException.
     * Liquibase changeset 036 deduplicates and adds a UNIQUE constraint so
     * this method becomes equivalent to findByEmail once migration is applied.
     */
    Optional<User> findFirstByEmail(String email);

    Optional<User> findFirstByOrderByEmailAsc();

    /**
     * Search users by email, optionally filtered by société membership.
     * Returns users that are members of the given société.
     */
    @Query("""
            SELECT u FROM User u
            JOIN com.yem.hlm.backend.societe.domain.AppUserSociete aus
              ON aus.id.userId = u.id
            WHERE aus.id.societeId = :societeId
              AND aus.actif = true
              AND (:q IS NULL OR lower(u.email) LIKE lower(concat('%', cast(:q as string), '%')))
            ORDER BY u.email ASC
            """)
    List<User> searchBySociete(@Param("societeId") UUID societeId, @Param("q") String q);

    /**
     * Returns enabled users who are members of the given société and whose
     * membership role maps to any of the given UserRoles.
     * Used by the prospect follow-up reminder scheduler.
     */
    @Query("""
            SELECT u FROM User u
            JOIN com.yem.hlm.backend.societe.domain.AppUserSociete aus
              ON aus.id.userId = u.id
            WHERE aus.id.societeId = :societeId
              AND aus.actif = true
              AND u.enabled = true
              AND aus.role IN :roles
            ORDER BY u.email ASC
            """)
    List<User> findBySocieteIdAndRoleInAndEnabledTrue(
            @Param("societeId") UUID societeId,
            @Param("roles") Set<String> roles
    );

    /** Test helper: override lockedUntil directly to simulate lock expiry in ITs. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :id")
    void setLockedUntilForTest(@Param("id") UUID id, @Param("lockedUntil") Instant lockedUntil);

    /** Find user by invitation token (used during account activation). */
    Optional<User> findByInvitationToken(String invitationToken);
}
