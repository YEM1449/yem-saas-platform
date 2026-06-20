package com.yem.hlm.backend.vente.repo;

import com.yem.hlm.backend.vente.domain.VenteDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenteDocumentRepository extends JpaRepository<VenteDocument, UUID> {

    Optional<VenteDocument> findBySocieteIdAndId(UUID societeId, UUID id);

    /**
     * Société- <i>and</i> vente-scoped lookup. The {@code vente_id} predicate is the access-control
     * boundary for the buyer portal: a document is only reachable through the vente it is attached to,
     * so a caller cannot read another vente's (or another buyer's) document by guessing its id.
     */
    Optional<VenteDocument> findBySocieteIdAndVente_IdAndId(UUID societeId, UUID venteId, UUID id);

    List<VenteDocument> findAllByVente_IdOrderByCreatedAtDesc(UUID venteId);
}
