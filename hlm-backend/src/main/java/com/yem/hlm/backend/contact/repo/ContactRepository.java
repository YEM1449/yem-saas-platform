package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    Optional<Contact> findBySocieteIdAndId(UUID societeId, UUID id);

    /** Case-insensitive email lookup within a société — used by portal magic-link auth. */
    Optional<Contact> findBySocieteIdAndEmailIgnoreCase(UUID societeId, String email);

    boolean existsBySocieteIdAndEmail(UUID societeId, String email);

    /**
     * Returns distinct societeIds that have at least one non-deleted contact with one of the given statuses.
     * Used by ReminderService to iterate only over relevant sociétés without loading all contacts.
     */
    @Query("""
            SELECT DISTINCT c.societeId FROM Contact c
            WHERE c.status IN :statuses
              AND c.deleted = false
            """)
    List<UUID> findDistinctSocieteIdsWithProspectStatus(@Param("statuses") List<ContactStatus> statuses);

    boolean existsBySocieteIdAndEmailAndIdNot(UUID societeId, String email, UUID id);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.societeId = :societeId AND c.status IN :statuses AND c.deleted = false")
    long countActiveProspects(@Param("societeId") UUID societeId,
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
            WHERE c.societeId = :societeId
              AND c.deleted   = false
              AND pd.source   IS NOT NULL
            GROUP BY pd.source
            ORDER BY COUNT(pd.contactId) DESC
            """)
    List<Object[]> prospectSourceFunnel(
            @Param("societeId")         UUID societeId,
            @Param("convertedStatuses") List<ContactStatus> convertedStatuses
    );

    /**
     * Finds soft-deleted contacts that have not yet been anonymized and whose
     * {@code updatedAt} is older than the given retention cutoff.
     * Used by {@link com.yem.hlm.backend.gdpr.scheduler.DataRetentionScheduler}.
     */
    @Query("""
            SELECT c FROM Contact c
            WHERE c.societeId    = :societeId
              AND c.deleted       = true
              AND c.anonymizedAt  IS NULL
              AND c.updatedAt     < :cutoff
            """)
    List<Contact> findRetentionCandidates(
            @Param("societeId") UUID societeId,
            @Param("cutoff")    java.time.LocalDateTime cutoff
    );

    @Query("""
            select c from Contact c
            where c.societeId = :societeId
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
            @Param("societeId") UUID societeId,
            @Param("filterByType") boolean filterByType,
            @Param("contactTypes") Collection<ContactType> contactTypes,
            @Param("status") ContactStatus status,
            @Param("q") String q,
            Pageable pageable
    );

    long countBySocieteIdAndDeletedFalse(UUID societeId);
}
