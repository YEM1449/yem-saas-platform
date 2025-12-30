package com.yem.hlm.backend.tenant.repo;

import com.yem.hlm.backend.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface TenantRepository extends JpaRepository <Tenant, UUID> {
    Optional<Tenant> findByKey(String key);
}
