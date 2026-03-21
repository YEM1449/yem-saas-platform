package com.yem.hlm.backend.media.repo;

import com.yem.hlm.backend.media.domain.PropertyMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    /** Société-scoped lookup by ID. */
    Optional<PropertyMedia> findBySocieteIdAndId(UUID societeId, UUID id);

    /** All media for a property, ordered by sort_order ASC. */
    List<PropertyMedia> findBySocieteIdAndPropertyIdOrderBySortOrderAsc(UUID societeId, UUID propertyId);

    /** Count of media files for a property (used in PropertyResponse.mediaCount). */
    @Query("SELECT COUNT(m) FROM PropertyMedia m WHERE m.societeId = :societeId AND m.propertyId = :propertyId")
    int countByTenantAndProperty(@Param("societeId") UUID societeId, @Param("propertyId") UUID propertyId);

    /** Next sort_order for a property (max + 1, 0 if none). */
    @Query("SELECT COALESCE(MAX(m.sortOrder) + 1, 0) FROM PropertyMedia m " +
           "WHERE m.societeId = :societeId AND m.propertyId = :propertyId")
    int nextSortOrder(@Param("societeId") UUID societeId, @Param("propertyId") UUID propertyId);
}
