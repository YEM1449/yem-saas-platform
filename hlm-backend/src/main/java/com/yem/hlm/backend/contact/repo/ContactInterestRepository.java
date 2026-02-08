package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ContactInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactInterestRepository extends JpaRepository<ContactInterest, UUID> {

    boolean existsByTenant_IdAndContactIdAndPropertyId(UUID tenantId, UUID contactId, UUID propertyId);

    Optional<ContactInterest> findByTenant_IdAndContactIdAndPropertyId(UUID tenantId, UUID contactId, UUID propertyId);

    List<ContactInterest> findAllByTenant_IdAndContactId(UUID tenantId, UUID contactId);

    List<ContactInterest> findAllByTenant_IdAndPropertyId(UUID tenantId, UUID propertyId);
}
