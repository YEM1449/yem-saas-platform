package com.yem.hlm.backend.portal.repo;

import com.yem.hlm.backend.portal.domain.PortalToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PortalTokenRepository extends JpaRepository<PortalToken, UUID> {

    /** Lookup by the SHA-256 hash of the raw token. */
    Optional<PortalToken> findByTokenHash(String tokenHash);

    /**
     * Deletes all portal tokens that have expired or have already been used.
     *
     * @param now current timestamp — tokens with expiresAt before this are considered expired
     * @return number of deleted rows
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PortalToken pt WHERE pt.expiresAt < :now OR pt.usedAt IS NOT NULL")
    int deleteExpiredAndUsed(@Param("now") Instant now);
}
