package com.yem.hlm.backend.media.repo;

import com.yem.hlm.backend.media.domain.PropertyMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    /** Tenant-scoped lookup by ID. */
    Optional<PropertyMedia> findByTenant_IdAndId(UUID tenantId, UUID id);

    /** All media for a property, ordered by sort_order ASC. */
    List<PropertyMedia> findByTenant_IdAndPropertyIdOrderBySortOrderAsc(UUID tenantId, UUID propertyId);

    /** Count of media files for a property (used in PropertyResponse.mediaCount). */
    @Query("SELECT COUNT(m) FROM PropertyMedia m WHERE m.tenant.id = :tenantId AND m.propertyId = :propertyId")
    int countByTenantAndProperty(@Param("tenantId") UUID tenantId, @Param("propertyId") UUID propertyId);

    /** Next sort_order for a property (max + 1, 0 if none). */
    @Query("SELECT COALESCE(MAX(m.sortOrder) + 1, 0) FROM PropertyMedia m " +
           "WHERE m.tenant.id = :tenantId AND m.propertyId = :propertyId")
    int nextSortOrder(@Param("tenantId") UUID tenantId, @Param("propertyId") UUID propertyId);
}
