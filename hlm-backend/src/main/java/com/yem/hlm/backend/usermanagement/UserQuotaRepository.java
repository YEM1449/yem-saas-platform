package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.usermanagement.domain.UserQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserQuotaRepository extends JpaRepository<UserQuota, UUID> {

    Optional<UserQuota> findBySocieteIdAndUserIdAndYearMonth(UUID societeId, UUID userId, String yearMonth);
}
