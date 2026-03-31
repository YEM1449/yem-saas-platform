package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.dashboard.api.dto.ImmeubleKpiRow;
import com.yem.hlm.backend.dashboard.api.dto.ProjectKpiRow;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides per-project and per-building (immeuble) inventory KPIs.
 *
 * <p>A single aggregate query is executed per endpoint, then the rows are
 * grouped in-memory to avoid multiple round-trips. This keeps the query
 * budget at 1 per endpoint rather than N (number of projects / buildings).
 */
@Service
@Transactional(readOnly = true)
public class KpiDashboardService {

    private final PropertyRepository propertyRepository;

    public KpiDashboardService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    // =========================================================================
    // By project
    // =========================================================================

    /**
     * Returns one {@link ProjectKpiRow} per project that has at least one property
     * in this société. The rows are ordered by project name (ascending).
     *
     * @param societeId required — calling code must already have validated it
     */
    public List<ProjectKpiRow> kpiByProject(UUID societeId) {
        List<Object[]> rows = propertyRepository.inventoryByProjectAndStatus(societeId);

        // Group rows by projectId
        Map<UUID, ProjectAcc> acc = new LinkedHashMap<>();
        for (Object[] r : rows) {
            UUID         projectId   = (UUID) r[0];
            String       projectName = (String) r[1];
            PropertyStatus status    = (PropertyStatus) r[2];
            long           count     = ((Number) r[3]).longValue();

            acc.computeIfAbsent(projectId, id -> new ProjectAcc(id, projectName))
               .add(status, count);
        }

        List<ProjectKpiRow> result = new ArrayList<>(acc.size());
        for (ProjectAcc a : acc.values()) {
            result.add(a.toRow());
        }
        return result;
    }

    // =========================================================================
    // By immeuble
    // =========================================================================

    /**
     * Returns one {@link ImmeubleKpiRow} per building that has at least one property
     * in this société. Properties without an immeuble are excluded.
     *
     * @param societeId required — calling code must already have validated it
     */
    public List<ImmeubleKpiRow> kpiByImmeuble(UUID societeId) {
        List<Object[]> rows = propertyRepository.inventoryByImmeubleAndStatus(societeId);

        // Group rows by immeubleId
        Map<UUID, ImmeubleAcc> acc = new LinkedHashMap<>();
        for (Object[] r : rows) {
            UUID           immeubleId   = (UUID) r[0];
            String         immeubleName = (String) r[1];
            UUID           projectId    = (UUID) r[2];
            String         projectName  = (String) r[3];
            PropertyStatus status       = (PropertyStatus) r[4];
            long           count        = ((Number) r[5]).longValue();

            acc.computeIfAbsent(immeubleId, id -> new ImmeubleAcc(id, immeubleName, projectId, projectName))
               .add(status, count);
        }

        List<ImmeubleKpiRow> result = new ArrayList<>(acc.size());
        for (ImmeubleAcc a : acc.values()) {
            result.add(a.toRow());
        }
        return result;
    }

    // =========================================================================
    // Private accumulators
    // =========================================================================

    private static final class ProjectAcc {
        final UUID   projectId;
        final String projectName;
        long available;
        long reserved;
        long sold;

        ProjectAcc(UUID projectId, String projectName) {
            this.projectId   = projectId;
            this.projectName = projectName;
        }

        void add(PropertyStatus status, long count) {
            switch (status) {
                case ACTIVE  -> available += count;
                case RESERVED -> reserved += count;
                case SOLD    -> sold     += count;
                default      -> { /* DRAFT, WITHDRAWN, ARCHIVED excluded from KPI rates */ }
            }
        }

        ProjectKpiRow toRow() {
            long total = available + reserved + sold;
            return new ProjectKpiRow(
                    projectId, projectName,
                    total, available, reserved, sold,
                    total > 0 ? (double) sold     / total : 0.0,
                    total > 0 ? (double) reserved / total : 0.0
            );
        }
    }

    private static final class ImmeubleAcc {
        final UUID   immeubleId;
        final String immeubleName;
        final UUID   projectId;
        final String projectName;
        long available;
        long reserved;
        long sold;

        ImmeubleAcc(UUID immeubleId, String immeubleName, UUID projectId, String projectName) {
            this.immeubleId   = immeubleId;
            this.immeubleName = immeubleName;
            this.projectId    = projectId;
            this.projectName  = projectName;
        }

        void add(PropertyStatus status, long count) {
            switch (status) {
                case ACTIVE   -> available += count;
                case RESERVED -> reserved  += count;
                case SOLD     -> sold      += count;
                default       -> { /* DRAFT, WITHDRAWN, ARCHIVED excluded from KPI rates */ }
            }
        }

        ImmeubleKpiRow toRow() {
            long total = available + reserved + sold;
            return new ImmeubleKpiRow(
                    immeubleId, immeubleName,
                    projectId, projectName,
                    total, available, reserved, sold,
                    total > 0 ? (double) sold     / total : 0.0,
                    total > 0 ? (double) reserved / total : 0.0
            );
        }
    }
}
