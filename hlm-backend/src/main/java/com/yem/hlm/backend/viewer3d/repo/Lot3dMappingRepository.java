package com.yem.hlm.backend.viewer3d.repo;

import com.yem.hlm.backend.viewer3d.domain.Lot3dMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface Lot3dMappingRepository extends JpaRepository<Lot3dMapping, UUID> {

    List<Lot3dMapping> findBySocieteIdAndProjetId(UUID societeId, UUID projetId);

    Optional<Lot3dMapping> findBySocieteIdAndProjetIdAndMeshId(UUID societeId, UUID projetId, String meshId);

    void deleteAllBySocieteIdAndProjetId(UUID societeId, UUID projetId);
}
