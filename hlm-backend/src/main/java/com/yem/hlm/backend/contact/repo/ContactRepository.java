package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByTenant_IdAndId(UUID tenantId, UUID id);

    boolean existsByTenant_IdAndEmail(UUID tenantId, String email);

    boolean existsByTenant_IdAndEmailAndIdNot(UUID tenantId, String email, UUID id);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.tenant.id = :tenantId AND c.status IN :statuses AND c.deleted = false")
    long countActiveProspects(@Param("tenantId") UUID tenantId,
                              @Param("statuses") List<ContactStatus> statuses);

    /**
     * Prospect source funnel: count all prospects by source and count converted ones.
     * "Converted" = status CLIENT or beyond (CLIENT, ACTIVE_CLIENT, COMPLETED_CLIENT, REFERRAL).
     * Returns rows: [source(String), totalCount(Long), convertedCount(Long)].
     * Only includes prospects that have a non-null source in ProspectDetail.
     */
    @Query("""
            SELECT pd.source,
                   COUNT(pd.contactId),
                   SUM(CASE WHEN c.status IN :convertedStatuses THEN 1L ELSE 0L END)
            FROM com.yem.hlm.backend.contact.domain.ProspectDetail pd
            JOIN pd.contact c
            WHERE c.tenant.id = :tenantId
              AND c.deleted   = false
              AND pd.source   IS NOT NULL
            GROUP BY pd.source
            ORDER BY COUNT(pd.contactId) DESC
            """)
    List<Object[]> prospectSourceFunnel(
            @Param("tenantId")          UUID tenantId,
            @Param("convertedStatuses") List<ContactStatus> convertedStatuses
    );

    @Query("""
            select c from Contact c
            where c.tenant.id = :tenantId
              and (:filterByType = false or c.contactType IN :contactTypes)
              and (cast(:status as string) is null or c.status = :status)
              and (
                   cast(:q as string) is null
                or lower(c.firstName) like lower(concat('%', cast(:q as string), '%'))
                or lower(c.lastName)  like lower(concat('%', cast(:q as string), '%'))
                or lower(c.email)     like lower(concat('%', cast(:q as string), '%'))
                or lower(c.phone)     like lower(concat('%', cast(:q as string), '%'))
              )
            """)
    Page<Contact> search(
            @Param("tenantId") UUID tenantId,
            @Param("filterByType") boolean filterByType,
            @Param("contactTypes") Collection<ContactType> contactTypes,
            @Param("status") ContactStatus status,
            @Param("q") String q,
            Pageable pageable
    );
}
