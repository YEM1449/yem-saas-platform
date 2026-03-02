package com.yem.hlm.backend.user.repo;

import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenant_IdAndEmail(UUID tenantId, String email);
    Optional<User> findByTenant_IdAndId(UUID tenantId, UUID id);
    Optional<User> findFirstByTenant_IdOrderByEmailAsc(UUID tenantId);

    /** Enabled users in a tenant with any of the given roles. Used by reminder notifications. */
    List<User> findByTenant_IdAndRoleInAndEnabledTrue(UUID tenantId, Collection<UserRole> roles);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenant.id = :tenantId
              AND (:q IS NULL OR lower(u.email) LIKE lower(concat('%', cast(:q as string), '%')))
            ORDER BY u.email ASC
            """)
    List<User> searchByTenant(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
