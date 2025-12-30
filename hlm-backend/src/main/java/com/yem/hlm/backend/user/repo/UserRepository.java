package com.yem.hlm.backend.user.repo;

import com.yem.hlm.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenantAndEmail(UUID tenantId, String email);
}
