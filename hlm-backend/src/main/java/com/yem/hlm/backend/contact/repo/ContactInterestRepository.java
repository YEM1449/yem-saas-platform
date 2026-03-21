package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ContactInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactInterestRepository extends JpaRepository<ContactInterest, UUID> {

    boolean existsBySocieteIdAndContactIdAndPropertyId(UUID societeId, UUID contactId, UUID propertyId);

    Optional<ContactInterest> findBySocieteIdAndContactIdAndPropertyId(UUID societeId, UUID contactId, UUID propertyId);

    List<ContactInterest> findAllBySocieteIdAndContactId(UUID societeId, UUID contactId);

    List<ContactInterest> findAllBySocieteIdAndPropertyId(UUID societeId, UUID propertyId);
}
