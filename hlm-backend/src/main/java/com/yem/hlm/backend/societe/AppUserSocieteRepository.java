package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserSocieteRepository extends JpaRepository<AppUserSociete, AppUserSocieteId> {

    List<AppUserSociete> findByIdUserIdAndActifTrue(UUID userId);

    List<AppUserSociete> findByIdUserId(UUID userId);

    Optional<AppUserSociete> findByIdUserIdAndIdSocieteId(UUID userId, UUID societeId);

    @Query("SELECT aus FROM AppUserSociete aus WHERE aus.id.userId = :userId AND aus.id.societeId = :societeId AND aus.actif = true")
    Optional<AppUserSociete> findByUserIdAndSocieteIdAndActifTrue(@Param("userId") UUID userId, @Param("societeId") UUID societeId);

    @Query("SELECT aus FROM AppUserSociete aus WHERE aus.id.userId = :userId AND aus.id.societeId = :societeId")
    Optional<AppUserSociete> findByUserIdAndSocieteId(@Param("userId") UUID userId, @Param("societeId") UUID societeId);

    @Query("SELECT COUNT(aus) FROM AppUserSociete aus WHERE aus.id.societeId = :societeId AND aus.actif = true")
    long countBySocieteIdAndActifTrue(@Param("societeId") UUID societeId);

    @Query("SELECT COUNT(aus) FROM AppUserSociete aus WHERE aus.id.societeId = :societeId AND aus.role = :role AND aus.actif = true")
    long countBySocieteIdAndRoleAndActifTrue(@Param("societeId") UUID societeId, @Param("role") String role);

    long countByIdSocieteId(UUID societeId);

    List<AppUserSociete> findByIdSocieteId(UUID societeId);

    List<AppUserSociete> findByIdSocieteIdAndActifTrue(UUID societeId);
}
