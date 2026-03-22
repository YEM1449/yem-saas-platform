package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.domain.Societe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocieteRepository extends JpaRepository<Societe, UUID>, JpaSpecificationExecutor<Societe> {
    Optional<Societe> findByKey(String key);
    Optional<Societe> findByNomIgnoreCase(String nom);
    boolean existsByNomIgnoreCase(String nom);
    List<Societe> findAllByActifTrue();
}
