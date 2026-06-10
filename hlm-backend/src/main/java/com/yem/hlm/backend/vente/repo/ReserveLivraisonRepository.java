package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.ReserveLivraison;
import com.yem.hlm.backend.vente.domain.StatutReserve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReserveLivraisonRepository extends JpaRepository<ReserveLivraison, UUID> {

    List<ReserveLivraison> findBySocieteIdAndVenteIdOrderByDateConstatAsc(UUID societeId, UUID venteId);

    Optional<ReserveLivraison> findBySocieteIdAndId(UUID societeId, UUID id);

    /** Count reserves for a vente that are not yet lifted (used to decide RESERVES_LEVEES). */
    long countBySocieteIdAndVenteIdAndStatutNot(UUID societeId, UUID venteId, StatutReserve statut);
}
