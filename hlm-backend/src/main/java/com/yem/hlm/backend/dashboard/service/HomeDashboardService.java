package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.dashboard.api.dto.HomeDashboardDTO;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;
import com.yem.hlm.backend.task.repo.TaskRepository;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fast home-dashboard snapshot.
 *
 * <p>Role-adaptive:
 * <ul>
 *   <li>ADMIN / MANAGER: full société scope.</li>
 *   <li>AGENT: personal scope (own ventes and tasks).</li>
 * </ul>
 *
 * <p>≤ 10 aggregate queries per call; cached 30 s per (societeId, actorId, role) triple.
 */
@Service
@Transactional(readOnly = true)
public class HomeDashboardService {

    /** Terminal statuts excluded from "active pipeline" queries. */
    private static final List<VenteStatut> TERMINAL = List.of(VenteStatut.LIVRE, VenteStatut.ANNULE);

    private final VenteRepository       venteRepo;
    private final TaskRepository        taskRepo;
    private final ContactRepository     contactRepo;
    private final PropertyRepository    propertyRepo;
    private final ReservationRepository reservationRepo;

    public HomeDashboardService(VenteRepository venteRepo,
                                TaskRepository taskRepo,
                                ContactRepository contactRepo,
                                PropertyRepository propertyRepo,
                                ReservationRepository reservationRepo) {
        this.venteRepo       = venteRepo;
        this.taskRepo        = taskRepo;
        this.contactRepo     = contactRepo;
        this.propertyRepo    = propertyRepo;
        this.reservationRepo = reservationRepo;
    }

    @Cacheable(
            value = CacheConfig.HOME_DASHBOARD_CACHE,
            key   = "#societeId + ':' + #actorId + ':' + #role"
    )
    public HomeDashboardDTO getSnapshot(UUID societeId, UUID actorId, String role) {
        boolean isAgent = "ROLE_AGENT".equals(role);
        LocalDateTime now = LocalDateTime.now();

        // ── 1. Pipeline: ventes par statut ────────────────────────────────────
        Map<String, Long> ventesParStatut = new LinkedHashMap<>();
        BigDecimal caActivePipeline;
        long activeVentesCount;

        if (isAgent) {
            venteRepo.countByStatutForAgent(societeId, actorId, TERMINAL)
                    .forEach(r -> ventesParStatut.put(r[0].toString(), (Long) r[1]));
            activeVentesCount = ventesParStatut.values().stream().mapToLong(Long::longValue).sum();
            caActivePipeline  = venteRepo.sumPrixVenteForAgent(societeId, actorId, TERMINAL);
        } else {
            venteRepo.countByStatut(societeId, TERMINAL)
                    .forEach(r -> ventesParStatut.put(r[0].toString(), (Long) r[1]));
            activeVentesCount = ventesParStatut.values().stream().mapToLong(Long::longValue).sum();
            caActivePipeline  = venteRepo.sumPrixVente(societeId, TERMINAL);
        }
        if (caActivePipeline == null) caActivePipeline = BigDecimal.ZERO;

        // ── 2. Inventory snapshot ─────────────────────────────────────────────
        Map<String, Long> byStatus = new LinkedHashMap<>();
        propertyRepo.inventoryByStatus(societeId, null)
                .forEach(r -> byStatus.put(r[0].toString(), toLong(r[1])));

        long biensDraft    = byStatus.getOrDefault("DRAFT",    0L);
        long biensActifs   = byStatus.getOrDefault("ACTIVE",   0L);
        long biensReserves = byStatus.getOrDefault("RESERVED", 0L);
        long biensVendus   = byStatus.getOrDefault("SOLD",     0L);

        BigDecimal tauxAbsorption = null;
        long marketable = biensActifs + biensReserves + biensVendus;
        if (marketable > 0) {
            tauxAbsorption = BigDecimal.valueOf(biensVendus)
                    .divide(BigDecimal.valueOf(marketable), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        // ── 3. Prospects actifs (tenant-wide) ─────────────────────────────────
        long activeProspectsCount = contactRepo.countActiveProspects(
                societeId, List.of(ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT));

        // ── 4. Réservations actives ───────────────────────────────────────────
        long activeReservationsCount = reservationRepo.countBySocieteIdAndStatus(societeId, ReservationStatus.ACTIVE);
        long expirant = reservationRepo.countExpiringBefore(societeId, now, now.plusHours(48));

        // ── 5. Tâches ─────────────────────────────────────────────────────────
        long openTasks, overdueTasks, todayTasks;
        if (isAgent) {
            openTasks    = taskRepo.countOpenByAssignee(societeId, actorId);
            overdueTasks = taskRepo.countOverdueByAssignee(societeId, actorId, now);
            todayTasks   = taskRepo.countDueTodayByAssignee(societeId, actorId,
                    now.toLocalDate().atStartOfDay(),
                    now.toLocalDate().atStartOfDay().plusDays(1));
        } else {
            openTasks    = taskRepo.countOpenBySociete(societeId);
            overdueTasks = taskRepo.countOverdueBySociete(societeId, now);
            todayTasks   = 0L; // societe-wide today count not needed for admin (expensive without index gain)
        }

        // ── 6. Recent ventes widget (last 5) ──────────────────────────────────
        List<Vente> rawVentes;
        if (isAgent) {
            rawVentes = venteRepo.findRecentForAgent(societeId, actorId, PageRequest.of(0, 5));
        } else {
            rawVentes = venteRepo.findAllBySocieteIdOrderByCreatedAtDesc(societeId)
                    .stream().limit(5).toList();
        }
        List<HomeDashboardDTO.RecentVenteRow> recentVentes = rawVentes.stream()
                .map(v -> new HomeDashboardDTO.RecentVenteRow(
                        v.getId(),
                        v.getContact() != null ? v.getContact().getFullName() : null,
                        v.getStatut().name(),
                        v.getPrixVente(),
                        v.getCreatedAt()))
                .toList();

        // ── 7. Urgent tasks widget (overdue + today, up to 8) ─────────────────
        List<HomeDashboardDTO.UrgentTaskRow> urgentTasks = List.of();
        if (isAgent) {
            LocalDateTime horizon = now.toLocalDate().atStartOfDay().plusDays(1);
            List<Task> rawTasks = taskRepo.findUrgent(societeId, actorId, horizon, PageRequest.of(0, 8));
            urgentTasks = rawTasks.stream()
                    .map(t -> new HomeDashboardDTO.UrgentTaskRow(
                            t.getId(), t.getTitle(), t.getStatus().name(),
                            t.getDueDate(), t.getContactId()))
                    .toList();
        }

        return new HomeDashboardDTO(
                now,
                activeVentesCount, caActivePipeline, ventesParStatut,
                biensDraft, biensActifs, biensReserves, biensVendus, tauxAbsorption,
                marketable,
                activeProspectsCount, activeReservationsCount, expirant,
                openTasks, overdueTasks, todayTasks,
                recentVentes, urgentTasks
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long l) return l;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }
}
