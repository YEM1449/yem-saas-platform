package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ProspectDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProspectDetailRepository extends JpaRepository<ProspectDetail, UUID> {
    Optional<ProspectDetail> findByContact_Tenant_IdAndContactId(UUID tenantId, UUID contactId);
}
