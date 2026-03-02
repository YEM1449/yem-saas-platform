package com.yem.hlm.backend.portal.repo;

import com.yem.hlm.backend.portal.domain.PortalToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PortalTokenRepository extends JpaRepository<PortalToken, UUID> {

    /** Lookup by the SHA-256 hash of the raw token. */
    Optional<PortalToken> findByTokenHash(String tokenHash);
}
