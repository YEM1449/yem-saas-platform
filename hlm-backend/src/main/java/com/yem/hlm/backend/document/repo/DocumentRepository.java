package com.yem.hlm.backend.document.repo;

import com.yem.hlm.backend.document.domain.Document;
import com.yem.hlm.backend.document.domain.DocumentEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findBySocieteIdAndId(UUID societeId, UUID id);

    List<Document> findBySocieteIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID societeId, DocumentEntityType entityType, UUID entityId);
}
