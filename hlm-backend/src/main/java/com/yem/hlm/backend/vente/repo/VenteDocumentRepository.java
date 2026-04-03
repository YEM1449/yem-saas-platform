package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.VenteDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenteDocumentRepository extends JpaRepository<VenteDocument, UUID> {

    Optional<VenteDocument> findBySocieteIdAndId(UUID societeId, UUID id);

    List<VenteDocument> findAllByVente_IdOrderByCreatedAtDesc(UUID venteId);
}
