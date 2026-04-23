package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.dashboard.api.dto.HomeDashboardDTO;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.task.domain.Task;
import com.yem.hlm.backend.task.domain.TaskStatus;
import com.yem.hlm.backend.task.repo.TaskRepository;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.usermanagement.UserQuotaRepository;
import com.yem.hlm.backend.usermanagement.domain.UserQuota;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final SocieteRepository       societeRepo;
    private final TrancheRepository       trancheRepo;
    private final UserQuotaRepository     quotaRepo;

    public HomeDashboardService(VenteRepository venteRepo,
                                VenteEcheanceRepository echeanceRepo,
                                TaskRepository taskRepo,
                                ContactRepository contactRepo,
                                PropertyRepository propertyRepo,
                                ReservationRepository reservationRepo,
                                SocieteRepository societeRepo,
                                TrancheRepository trancheRepo,
                                UserQuotaRepository quotaRepo) {
        this.venteRepo       = venteRepo;
        this.echeanceRepo    = echeanceRepo;
        this.taskRepo        = taskRepo;
        this.contactRepo     = contactRepo;
        this.propertyRepo    = propertyRepo;
        this.reservationRepo = reservationRepo;
        this.societeRepo     = societeRepo;
        this.trancheRepo     = trancheRepo;
        this.quotaRepo       = quotaRepo;
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

        long ventesSigneesMoisCourantCount = isAgent
                ? venteRepo.countInPeriodExcludingForAgent(societeId, actorId, moisFrom, moisTo, ANNULE_ONLY)
                : venteRepo.countInPeriodExcluding(societeId, moisFrom, moisTo, ANNULE_ONLY);

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

        // ── 11. Owner KPIs (cancellation rate, avg ticket, conversion, encaissé, leaderboard) ──
        LocalDateTime ninetyDaysAgo = now.minusDays(90);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        long ventes90d  = venteRepo.countCreatedInPeriod(societeId, ninetyDaysAgo, now);
        long annule90d  = venteRepo.countByStatutInPeriod(societeId, VenteStatut.ANNULE, ninetyDaysAgo, now);
        BigDecimal cancellationRate90d = ventes90d > 0
                ? BigDecimal.valueOf(annule90d)
                        .divide(BigDecimal.valueOf(ventes90d), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgTicketLivre = venteRepo.avgPrixVenteByStatut(societeId, VenteStatut.LIVRE);
        if (avgTicketLivre == null) avgTicketLivre = BigDecimal.ZERO;

        long ventes30d       = venteRepo.countCreatedInPeriod(societeId, thirtyDaysAgo, now);
        long reservations30d = reservationRepo.countCreatedInPeriod(societeId, thirtyDaysAgo, now);
        BigDecimal conversionRate30d = reservations30d > 0
                ? BigDecimal.valueOf(ventes30d)
                        .divide(BigDecimal.valueOf(reservations30d), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : null;

        BigDecimal encaisseMois = echeanceRepo.sumPaidInPeriod(
                societeId,
                currentMonth.atDay(1),
                currentMonth.plusMonths(1).atDay(1));
        if (encaisseMois == null) encaisseMois = BigDecimal.ZERO;

        List<HomeDashboardDTO.AgentLeaderboardRow> topAgents = List.of();
        if (!isAgent) {
            List<Object[]> rawAgents = venteRepo.topAgentsByCA(societeId, ninetyDaysAgo, now, PageRequest.of(0, 5));
            topAgents = rawAgents.stream()
                    .map(r -> {
                        String prenom    = r[1] != null ? r[1].toString() : "";
                        String nomFamille = r[2] != null ? r[2].toString() : "";
                        String fullName  = (prenom + " " + nomFamille).trim();
                        if (fullName.isEmpty()) fullName = "—";
                        return new HomeDashboardDTO.AgentLeaderboardRow(
                                (UUID) r[0],
                                fullName,
                                (BigDecimal) r[3],
                                ((Number) r[4]).longValue());
                    })
                    .toList();
        }

        // ── 12. Executive KPIs (Wave 13 — Owner view) ────────────────────────
        BigDecimal caYtd               = BigDecimal.ZERO;
        BigDecimal caSameMonthLastYear = BigDecimal.ZERO;
        BigDecimal caYoYPct            = null;
        BigDecimal monthsOfSupply      = null;
        BigDecimal salesVelocityPerWeek;
        BigDecimal winRate90d          = null;
        BigDecimal dsoRolling90d       = null;
        BigDecimal collectionEff90d    = null;
        BigDecimal caMensuelCible      = null;
        Long ventesMensuelCible        = null;
        BigDecimal quotaAttainmentMtd  = null;
        BigDecimal quotaVentesAttainmentMtd = null;
        List<HomeDashboardDTO.UpcomingDeliveryRow> upcomingDeliveries = List.of();

        if (!isAgent) {
            // CA YTD (Jan 1 → now), excluding ANNULE
            LocalDateTime ytdFrom = now.toLocalDate().withDayOfYear(1).atStartOfDay();
            BigDecimal ytdTmp = venteRepo.sumPrixVenteInPeriod(societeId, ytdFrom, now, ANNULE_ONLY);
            caYtd = ytdTmp != null ? ytdTmp : BigDecimal.ZERO;

            // CA same month last year → YoY
            YearMonth sameMonthLastYear = currentMonth.minusYears(1);
            LocalDateTime smlyFrom = sameMonthLastYear.atDay(1).atStartOfDay();
            LocalDateTime smlyTo   = sameMonthLastYear.plusMonths(1).atDay(1).atStartOfDay();
            BigDecimal smlyTmp = venteRepo.sumPrixVenteInPeriod(societeId, smlyFrom, smlyTo, ANNULE_ONLY);
            caSameMonthLastYear = smlyTmp != null ? smlyTmp : BigDecimal.ZERO;
            if (caSameMonthLastYear.signum() > 0) {
                caYoYPct = caSigneMoisCourant.subtract(caSameMonthLastYear)
                        .divide(caSameMonthLastYear, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }

            // Months of supply = biensActifs / (ventes90d / 3)
            if (ventes90d > 0 && biensActifs > 0) {
                BigDecimal monthlyRun = BigDecimal.valueOf(ventes90d)
                        .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
                if (monthlyRun.signum() > 0) {
                    monthsOfSupply = BigDecimal.valueOf(biensActifs)
                            .divide(monthlyRun, 1, RoundingMode.HALF_UP);
                }
            }

            // Sales velocity: ventes last 28 days / 4
            LocalDateTime twentyEightDaysAgo = now.minusDays(28);
            long ventes28d = venteRepo.countCreatedInPeriod(societeId, twentyEightDaysAgo, now);
            salesVelocityPerWeek = BigDecimal.valueOf(ventes28d)
                    .divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);

            // Win rate 90d = LIVRE / (LIVRE + ANNULE)
            long livre90d = venteRepo.countByStatutInPeriod(societeId, VenteStatut.LIVRE, ninetyDaysAgo, now);
            long terminal90d = livre90d + annule90d;
            if (terminal90d > 0) {
                winRate90d = BigDecimal.valueOf(livre90d)
                        .divide(BigDecimal.valueOf(terminal90d), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }

            // Collection efficiency 90d (paid ÷ due, window = [today-90d, today))
            LocalDate ninetyDaysAgoDate = today.minusDays(90);
            BigDecimal duePast90d  = echeanceRepo.sumMontantDueInPeriodAll(societeId, ninetyDaysAgoDate, today);
            BigDecimal paidPast90d = echeanceRepo.sumPaidInPeriod(societeId, ninetyDaysAgoDate, today);
            if (duePast90d == null)  duePast90d  = BigDecimal.ZERO;
            if (paidPast90d == null) paidPast90d = BigDecimal.ZERO;
            if (duePast90d.signum() > 0) {
                collectionEff90d = paidPast90d
                        .divide(duePast90d, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }

            // DSO approximation: overdue amount ÷ daily paid run-rate on trailing 90d
            if (paidPast90d.signum() > 0) {
                BigDecimal dailyRun = paidPast90d.divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP);
                if (dailyRun.signum() > 0 && echeancesEnRetard.signum() > 0) {
                    dsoRolling90d = echeancesEnRetard.divide(dailyRun, 1, RoundingMode.HALF_UP);
                } else {
                    dsoRolling90d = BigDecimal.ZERO;
                }
            }

            // Per-user quota lookup: prefer user-specific target, fall back to société-wide
            String currentYearMonth = currentMonth.toString(); // "YYYY-MM"
            UserQuota userQuota = quotaRepo.findBySocieteIdAndUserIdAndYearMonth(
                    societeId, actorId, currentYearMonth).orElse(null);

            if (userQuota != null) {
                caMensuelCible     = userQuota.getCaCible();
                ventesMensuelCible = userQuota.getVentesCountCible();
            } else {
                Societe societe = societeRepo.findById(societeId).orElse(null);
                if (societe != null) {
                    caMensuelCible = societe.getCaMensuelCible();
                    if (societe.getVentesMensuelCible() != null) {
                        ventesMensuelCible = societe.getVentesMensuelCible().longValue();
                    }
                }
            }

            if (caMensuelCible != null && caMensuelCible.signum() > 0) {
                quotaAttainmentMtd = caSigneMoisCourant
                        .divide(caMensuelCible, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }
            if (ventesMensuelCible != null && ventesMensuelCible > 0) {
                quotaVentesAttainmentMtd = BigDecimal.valueOf(ventesSigneesMoisCourantCount)
                        .divide(BigDecimal.valueOf(ventesMensuelCible), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }

            // Upcoming deliveries: next 90 days, excluding LIVREE
            LocalDate horizonDate = today.plusDays(90);
            List<Object[]> rawDeliveries = trancheRepo.findUpcomingDeliveries(societeId, today, horizonDate);
            List<HomeDashboardDTO.UpcomingDeliveryRow> deliveries = new ArrayList<>(rawDeliveries.size());
            for (Object[] r : rawDeliveries) {
                UUID trancheId        = (UUID) r[0];
                String trancheNom     = r[1] != null ? r[1].toString() : null;
                Integer trancheNumero = r[2] != null ? ((Number) r[2]).intValue() : null;
                String label          = (trancheNom != null && !trancheNom.isBlank())
                        ? trancheNom
                        : (trancheNumero != null ? "Tranche " + trancheNumero : "Tranche");
                UUID projectId        = (UUID) r[3];
                String projectName    = r[4] != null ? r[4].toString() : "";
                LocalDate livraison   = toLocalDate(r[5]);
                long daysUntil        = livraison != null ? ChronoUnit.DAYS.between(today, livraison) : 0L;
                long totalUnits       = toLong(r[6]);
                long soldUnits        = toLong(r[7]);
                deliveries.add(new HomeDashboardDTO.UpcomingDeliveryRow(
                        trancheId, label, projectId, projectName,
                        livraison, daysUntil, totalUnits, soldUnits));
            }
            upcomingDeliveries = deliveries;
        } else {
            // Agent scope: only compute velocity (cheap, useful); leave owner-only fields null.
            LocalDateTime twentyEightDaysAgo = now.minusDays(28);
            long ventes28d = venteRepo.countCreatedInPeriod(societeId, twentyEightDaysAgo, now);
            salesVelocityPerWeek = BigDecimal.valueOf(ventes28d)
                    .divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);
        }

        // ── 13. Monthly CA trend (last 6 months) + project breakdown ─────────
        List<HomeDashboardDTO.MonthlyTrendPoint> monthlyTrend = List.of();
        List<HomeDashboardDTO.ProjectBreakdownRow> projectBreakdown = List.of();

        if (!isAgent) {
            LocalDateTime sixMonthsAgo = now.minusMonths(6).withDayOfMonth(1).toLocalDate().atStartOfDay();
            List<Object[]> rawTrend = venteRepo.monthlyCaTrend(societeId, sixMonthsAgo);

            // Build a full 6-month array, filling zero for missing months
            Map<String, BigDecimal> trendByMonth = new LinkedHashMap<>();
            for (Object[] r : rawTrend) {
                int yr = ((Number) r[0]).intValue();
                int mo = ((Number) r[1]).intValue();
                trendByMonth.put(String.format("%04d-%02d", yr, mo), toBigDecimal(r[2]));
            }

            List<HomeDashboardDTO.MonthlyTrendPoint> trend = new ArrayList<>(6);
            String[] FR_MONTHS = {"Jan","Fév","Mar","Avr","Mai","Jui","Jul","Aoû","Sep","Oct","Nov","Déc"};
            for (int i = 5; i >= 0; i--) {
                YearMonth ym = YearMonth.from(now.toLocalDate()).minusMonths(i);
                String key   = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
                String label = FR_MONTHS[ym.getMonthValue() - 1] + " " + String.valueOf(ym.getYear()).substring(2);
                trend.add(new HomeDashboardDTO.MonthlyTrendPoint(key, label,
                        trendByMonth.getOrDefault(key, BigDecimal.ZERO)));
            }
            monthlyTrend = trend;

            List<Object[]> rawProjects = venteRepo.topProjectsByCA(societeId);
            projectBreakdown = rawProjects.stream()
                    .map(r -> new HomeDashboardDTO.ProjectBreakdownRow(
                            r[0] != null ? r[0].toString() : null,
                            r[1] != null ? r[1].toString() : "—",
                            toBigDecimal(r[2]),
                            ((Number) r[3]).longValue()))
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
                cancellationRate90d, avgTicketLivre, conversionRate30d, encaisseMois, topAgents,
                caYtd, caSameMonthLastYear, caYoYPct,
                monthsOfSupply, salesVelocityPerWeek, winRate90d,
                dsoRolling90d, collectionEff90d,
                caMensuelCible, ventesMensuelCible, quotaAttainmentMtd,
                ventesSigneesMoisCourantCount, quotaVentesAttainmentMtd,
                upcomingDeliveries,
                monthlyTrend, projectBreakdown,
                recentVentes, urgentTasks
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Long l)    return BigDecimal.valueOf(l);
        if (o instanceof Integer i) return BigDecimal.valueOf(i);
        if (o instanceof Number n) {
            double v = n.doubleValue();
            return Double.isFinite(v) ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long l) return l;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.sql.Date sd) return sd.toLocalDate();
        if (o instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return null;
    }

    // ── Shareholder KPIs ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO getShareholderKpis(UUID societeId) {
        List<Object[]> rows = propertyRepo.inventoryByProjectStatusWithValues(societeId);

        Map<UUID, String>  nameMap  = new LinkedHashMap<>();
        Map<UUID, long[]>  unitMap  = new LinkedHashMap<>();
        BigDecimal portfolioValue   = BigDecimal.ZERO;

        for (Object[] r : rows) {
            if (r[0] == null) continue;
            UUID   projectId = (UUID) r[0];
            String projName  = r[1] != null ? r[1].toString() : "—";
            String status    = r[2] != null ? r[2].toString() : "";
            long   count     = toLong(r[3]);
            BigDecimal priceSum = toBigDecimal(r[4]);

            nameMap.putIfAbsent(projectId, projName);
            unitMap.computeIfAbsent(projectId, k -> new long[1]);
            unitMap.get(projectId)[0] += count;

            if ("ACTIVE".equals(status) || "RESERVED".equals(status)) {
                portfolioValue = portfolioValue.add(priceSum);
            }
        }

        long totalAllUnits = unitMap.values().stream().mapToLong(a -> a[0]).sum();

        List<com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO.ProjectConcentrationRow> concentration =
                nameMap.entrySet().stream()
                        .map(e -> {
                            long cnt = unitMap.get(e.getKey())[0];
                            double pct = totalAllUnits > 0
                                    ? Math.round(cnt * 1000.0 / totalAllUnits) / 10.0
                                    : 0.0;
                            return new com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO.ProjectConcentrationRow(
                                    e.getKey(), e.getValue(), cnt, pct);
                        })
                        .filter(row -> row.unitCount() > 0)
                        .sorted(java.util.Comparator.comparingDouble(
                                com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO.ProjectConcentrationRow::pctOfPortfolio).reversed())
                        .toList();

        BigDecimal soldValue = venteRepo.sumPrixVenteByStatut(societeId, VenteStatut.LIVRE);
        if (soldValue == null) soldValue = BigDecimal.ZERO;

        BigDecimal projectedExposure = venteRepo.sumPrixVente(societeId, TERMINAL);
        if (projectedExposure == null) projectedExposure = BigDecimal.ZERO;

        return new com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO(
                portfolioValue, soldValue, projectedExposure, concentration, LocalDateTime.now());
    }

    // ── Project Director KPIs ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO getProjectDirectorKpis(UUID societeId) {
        List<Object[]> rows = propertyRepo.inventoryByProjectAndStatus(societeId);

        Map<UUID, String> nameMap  = new LinkedHashMap<>();
        Map<UUID, long[]> countMap = new LinkedHashMap<>(); // [total, sold, reserved, active]

        for (Object[] r : rows) {
            if (r[0] == null) continue;
            UUID   projectId = (UUID) r[0];
            String projName  = r[1] != null ? r[1].toString() : "—";
            String status    = r[2] != null ? r[2].toString() : "";
            long   count     = toLong(r[3]);

            nameMap.putIfAbsent(projectId, projName);
            countMap.computeIfAbsent(projectId, k -> new long[4]);
            long[] c = countMap.get(projectId);
            c[0] += count;
            if ("SOLD".equals(status))     c[1] += count;
            if ("RESERVED".equals(status)) c[2] += count;
            if ("ACTIVE".equals(status))   c[3] += count;
        }

        // Earliest delivery per project
        Map<UUID, LocalDate> deliveryMap = new java.util.HashMap<>();
        trancheRepo.findEarliestDeliveryPerProject(societeId).forEach(r -> {
            if (r[0] == null) return;
            UUID projectId = UUID.fromString(r[0].toString());
            LocalDate date = toLocalDate(r[1]);
            if (date != null) deliveryMap.put(projectId, date);
        });

        LocalDate today = LocalDate.now();
        List<com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO.ProjectProgressRow> projects =
                nameMap.entrySet().stream()
                        .map(e -> {
                            UUID   pid      = e.getKey();
                            long[] c        = countMap.get(pid);
                            long total      = c[0], sold = c[1], reserved = c[2], available = c[3];
                            double soldPct  = total > 0 ? Math.round(sold * 1000.0 / total) / 10.0 : 0.0;
                            double resvPct  = total > 0 ? Math.round(reserved * 1000.0 / total) / 10.0 : 0.0;
                            LocalDate dlv   = deliveryMap.get(pid);
                            boolean onTrack = dlv == null || !dlv.isBefore(today);
                            return new com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO.ProjectProgressRow(
                                    pid, e.getValue(), total, sold, reserved, available,
                                    soldPct, resvPct, dlv, onTrack);
                        })
                        .filter(row -> row.totalUnits() > 0)
                        .sorted(java.util.Comparator.comparingDouble(
                                com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO.ProjectProgressRow::soldPct).reversed())
                        .toList();

        return new com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO(projects, LocalDateTime.now());
    }
}
