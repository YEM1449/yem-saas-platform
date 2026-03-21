package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserSocieteRepository extends JpaRepository<AppUserSociete, AppUserSocieteId> {
    List<AppUserSociete> findByIdUserIdAndActifTrue(UUID userId);
    Optional<AppUserSociete> findByIdUserIdAndIdSocieteId(UUID userId, UUID societeId);
}
