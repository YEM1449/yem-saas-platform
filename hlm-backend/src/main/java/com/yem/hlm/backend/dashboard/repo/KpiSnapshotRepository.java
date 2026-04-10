package com.yem.hlm.backend.dashboard.repo;

import com.yem.hlm.backend.dashboard.domain.KpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, UUID> {

    Optional<KpiSnapshot> findBySocieteIdAndTrancheId(UUID societeId, UUID trancheId);
}
