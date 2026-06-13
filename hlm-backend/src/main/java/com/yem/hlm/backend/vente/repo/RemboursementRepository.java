package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.Remboursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemboursementRepository extends JpaRepository<Remboursement, UUID> {

    Optional<Remboursement> findBySocieteIdAndVenteId(UUID societeId, UUID venteId);

    boolean existsByVenteId(UUID venteId);
}
