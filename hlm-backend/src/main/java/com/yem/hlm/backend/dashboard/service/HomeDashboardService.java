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
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
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
import java.time.YearMonth;
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

    private static final List<VenteStatut> ANNULE_ONLY = List.of(VenteStatut.ANNULE);

    private final VenteRepository         venteRepo;
    private final VenteEcheanceRepository echeanceRepo;
    private final TaskRepository          taskRepo;
    private final ContactRepository       contactRepo;
    private final PropertyRepository      propertyRepo;
    private final ReservationRepository   reservationRepo;

    public HomeDashboardService(VenteRepository venteRepo,
                                VenteEcheanceRepository echeanceRepo,
                                TaskRepository taskRepo,
                                ContactRepository contactRepo,
                                PropertyRepository propertyRepo,
                                ReservationRepository reservationRepo) {
        this.venteRepo       = venteRepo;
        this.echeanceRepo    = echeanceRepo;
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

        // ── 3. CA mensuel (trend) + CA Livré + Ventes stallées ───────────────
        YearMonth currentMonth  = YearMonth.from(now.toLocalDate());
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDateTime moisFrom  = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime moisTo    = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevFrom  = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime prevTo    = previousMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal caSigneMoisCourant, caSigneMoisPrecedent;
        if (isAgent) {
            caSigneMoisCourant  = venteRepo.sumPrixVenteInPeriodForAgent(societeId, actorId, moisFrom, moisTo, ANNULE_ONLY);
            caSigneMoisPrecedent = venteRepo.sumPrixVenteInPeriodForAgent(societeId, actorId, prevFrom, prevTo, ANNULE_ONLY);
        } else {
            caSigneMoisCourant  = venteRepo.sumPrixVenteInPeriod(societeId, moisFrom, moisTo, ANNULE_ONLY);
            caSigneMoisPrecedent = venteRepo.sumPrixVenteInPeriod(societeId, prevFrom, prevTo, ANNULE_ONLY);
        }
        if (caSigneMoisCourant  == null) caSigneMoisCourant  = BigDecimal.ZERO;
        if (caSigneMoisPrecedent == null) caSigneMoisPrecedent = BigDecimal.ZERO;

        BigDecimal caLivre = venteRepo.sumPrixVenteByStatut(societeId, VenteStatut.LIVRE);
        if (caLivre == null) caLivre = BigDecimal.ZERO;

        long ventesStalleesCount = isAgent ? 0L :
                venteRepo.countStalledVentes(societeId,
                        List.of(VenteStatut.COMPROMIS, VenteStatut.FINANCEMENT),
                        now.minusDays(30));

        // ── 4. Écheancier pulse ───────────────────────────────────────────────
        LocalDate today  = now.toLocalDate();
        LocalDate in30   = today.plusDays(30);
        BigDecimal echeancesA30Jours  = echeanceRepo.sumMontantDueInPeriod(societeId, today, in30);
        BigDecimal echeancesEnRetard  = echeanceRepo.sumMontantOverdue(societeId, today);
        long echeancesEnRetardCount   = echeanceRepo.countOverdue(societeId, today);
        if (echeancesA30Jours == null) echeancesA30Jours = BigDecimal.ZERO;
        if (echeancesEnRetard == null) echeancesEnRetard = BigDecimal.ZERO;

        // ── 6. Prospects actifs (tenant-wide) ─────────────────────────────────
        long activeProspectsCount = contactRepo.countActiveProspects(
                societeId, List.of(ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT));

        // ── 7. Réservations actives ───────────────────────────────────────────
        long activeReservationsCount = reservationRepo.countBySocieteIdAndStatus(societeId, ReservationStatus.ACTIVE);
        long expirant = reservationRepo.countExpiringBefore(societeId, now, now.plusHours(48));

        // ── 8. Tâches ─────────────────────────────────────────────────────────
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

        // ── 9. Recent ventes widget (last 5) ──────────────────────────────────
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
                        v.getVenteRef(),
                        v.getContact() != null ? v.getContact().getFullName() : null,
                        v.getStatut().name(),
                        v.getPrixVente(),
                        v.getCreatedAt()))
                .toList();

        // ── 10. Urgent tasks widget (overdue + today, up to 8) ────────────────
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
                caSigneMoisCourant, caSigneMoisPrecedent, caLivre,
                echeancesA30Jours, echeancesEnRetard, echeancesEnRetardCount,
                biensDraft, biensActifs, biensReserves, biensVendus, tauxAbsorption,
                marketable,
                activeProspectsCount, activeReservationsCount, expirant,
                ventesStalleesCount,
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
