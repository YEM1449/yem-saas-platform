package com.yem.hlm.backend.viewer3d.repo;

import com.yem.hlm.backend.viewer3d.domain.Project3dModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface Project3dModelRepository extends JpaRepository<Project3dModel, UUID> {

    Optional<Project3dModel> findBySocieteIdAndProjetId(UUID societeId, UUID projetId);

    boolean existsBySocieteIdAndProjetId(UUID societeId, UUID projetId);
}
