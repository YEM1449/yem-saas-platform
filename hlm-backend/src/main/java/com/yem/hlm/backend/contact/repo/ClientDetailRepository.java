package com.yem.hlm.backend.contact.repo;

import com.yem.hlm.backend.contact.domain.ClientDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientDetailRepository extends JpaRepository<ClientDetail, UUID> {
    Optional<ClientDetail> findByContact_Tenant_IdAndContactId(UUID tenantId, UUID contactId);
}
