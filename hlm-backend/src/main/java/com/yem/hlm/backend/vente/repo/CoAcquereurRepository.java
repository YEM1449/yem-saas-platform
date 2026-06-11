package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.CoAcquereur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoAcquereurRepository extends JpaRepository<CoAcquereur, UUID> {

    List<CoAcquereur> findBySocieteIdAndVenteIdOrderByCreatedAtAsc(UUID societeId, UUID venteId);

    Optional<CoAcquereur> findBySocieteIdAndId(UUID societeId, UUID id);

    boolean existsBySocieteIdAndVenteId(UUID societeId, UUID venteId);
}
