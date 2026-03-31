package com.yem.hlm.backend.immeuble.repo;

import com.yem.hlm.backend.immeuble.domain.Immeuble;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImmeubleRepository extends JpaRepository<Immeuble, UUID> {

    Optional<Immeuble> findBySocieteIdAndId(UUID societeId, UUID id);

    List<Immeuble> findBySocieteIdOrderByNomAsc(UUID societeId);

    List<Immeuble> findBySocieteIdAndProjectIdOrderByNomAsc(UUID societeId, UUID projectId);

    boolean existsBySocieteIdAndProjectIdAndNom(UUID societeId, UUID projectId, String nom);
}
