package com.yem.hlm.backend.groupe.repo;

import com.yem.hlm.backend.groupe.domain.ClientGroupeLien;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ClientGroupeLien}. This is a cross-société infrastructure table
 * (no per-société RLS), so queries here are not société-scoped — the service layer restricts
 * access to the owner's ADMIN sociétés.
 */
public interface ClientGroupeLienRepository extends JpaRepository<ClientGroupeLien, UUID> {

    Optional<ClientGroupeLien> findByContactId(UUID contactId);

    boolean existsByContactId(UUID contactId);

    List<ClientGroupeLien> findByGroupePersonneId(UUID groupePersonneId);

    List<ClientGroupeLien> findBySocieteIdIn(Collection<UUID> societeIds);

    List<ClientGroupeLien> findByContactIdIn(Collection<UUID> contactIds);
}
