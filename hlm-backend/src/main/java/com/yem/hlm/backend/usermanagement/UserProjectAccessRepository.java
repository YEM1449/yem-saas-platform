package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.usermanagement.domain.UserProjectAccess;
import com.yem.hlm.backend.usermanagement.domain.UserProjectAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserProjectAccessRepository extends JpaRepository<UserProjectAccess, UserProjectAccessId> {

    List<UserProjectAccess> findBySocieteIdAndUserId(UUID societeId, UUID userId);

    @Query("SELECT a.id.projectId FROM UserProjectAccess a WHERE a.societeId = :societeId AND a.id.userId = :userId")
    List<UUID> findProjectIdsBySocieteIdAndUserId(@Param("societeId") UUID societeId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserProjectAccess a WHERE a.societeId = :societeId AND a.id.userId = :userId")
    void deleteBySocieteIdAndUserId(@Param("societeId") UUID societeId, @Param("userId") UUID userId);
}
