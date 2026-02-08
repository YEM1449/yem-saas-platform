package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByTenant_IdAndId(UUID tenantId, UUID id);

    boolean existsByTenant_IdAndEmail(UUID tenantId, String email);

    boolean existsByTenant_IdAndEmailAndIdNot(UUID tenantId, String email, UUID id);

    @Query("""
            select c from Contact c
            where c.tenant.id = :tenantId
              and (:status is null or c.status = :status)
              and (
                   :q is null
                or lower(c.firstName) like lower(concat('%', :q, '%'))
                or lower(c.lastName)  like lower(concat('%', :q, '%'))
                or lower(c.email)     like lower(concat('%', :q, '%'))
                or lower(c.phone)     like lower(concat('%', :q, '%'))
              )
            """)
    Page<Contact> search(
            @Param("tenantId") UUID tenantId,
            @Param("status") ContactStatus status,
            @Param("q") String q,
            Pageable pageable
    );
}
