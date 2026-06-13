package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO.GroupTotals;
import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO.SocieteRow;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.Societe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * "Vue Groupe" — consolidated dashboard across every société where the current user
 * holds an <b>ADMIN</b> membership (the group-owner case: one person, several sociétés).
 *
 * <p><b>Isolation model.</b> This service never widens a query across sociétés. It verifies
 * each membership in {@code app_user_societe}, then summarizes <em>one société at a time</em>
 * by temporarily pointing {@code SocieteContext} at that société before calling
 * {@link GroupSocieteSummarizer#summarize(Societe)} (whose {@code @Transactional} boundary
 * lets {@code RlsContextAspect} set the matching RLS session variable). The original context
 * is restored in a {@code finally} block, so the rest of the request keeps the JWT's société.
 *
 * <p>MANAGER/AGENT memberships are deliberately excluded: the group view exposes owner-level
 * financials, and only an ADMIN of a société may see them.
 */
@Service
public class GroupDashboardService {

    private static final Logger log = LoggerFactory.getLogger(GroupDashboardService.class);

    private static final String ADMIN_ROLE = "ADMIN";

    private final AppUserSocieteRepository membershipRepository;
    private final SocieteRepository societeRepository;
    private final GroupSocieteSummarizer summarizer;
    private final SocieteContextHelper societeCtx;

    public GroupDashboardService(AppUserSocieteRepository membershipRepository,
                                 SocieteRepository societeRepository,
                                 GroupSocieteSummarizer summarizer,
                                 SocieteContextHelper societeCtx) {
        this.membershipRepository = membershipRepository;
        this.societeRepository = societeRepository;
        this.summarizer = summarizer;
        this.societeCtx = societeCtx;
    }

    public GroupDashboardDTO getGroupDashboard() {
        UUID userId = societeCtx.requireUserId();

        List<UUID> adminSocieteIds = membershipRepository.findByIdUserIdAndActifTrue(userId).stream()
                .filter(m -> ADMIN_ROLE.equals(m.getRole()))
                .map(AppUserSociete::getSocieteId)
                .toList();

        if (adminSocieteIds.isEmpty()) {
            throw new AccessDeniedException("Vue Groupe requires an ADMIN membership in at least one société");
        }

        List<SocieteRow> rows = new ArrayList<>(adminSocieteIds.size());
        UUID originalSocieteId = SocieteContext.getSocieteId();
        try {
            for (UUID societeId : adminSocieteIds) {
                Societe societe = societeRepository.findById(societeId).orElse(null);
                if (societe == null || !societe.isActif()) {
                    continue;
                }
                SocieteContext.setSocieteId(societeId);
                rows.add(summarizer.summarize(societe));
            }
        } finally {
            SocieteContext.setSocieteId(originalSocieteId);
        }

        rows.sort(Comparator.comparing(SocieteRow::caConfirme).reversed());
        log.debug("Vue Groupe computed for userId={} over {} société(s)", userId, rows.size());
        return new GroupDashboardDTO(totalsOf(rows), rows);
    }

    private static GroupTotals totalsOf(List<SocieteRow> rows) {
        long disponibles = rows.stream().mapToLong(SocieteRow::unitsDisponibles).sum();
        long reserves    = rows.stream().mapToLong(SocieteRow::unitsReserves).sum();
        long vendus      = rows.stream().mapToLong(SocieteRow::unitsVendus).sum();
        return new GroupTotals(
                rows.size(),
                disponibles, reserves, vendus,
                GroupSocieteSummarizer.absorptionPct(disponibles, reserves, vendus),
                sum(rows, SocieteRow::caConfirme),
                sum(rows, SocieteRow::caEnCours),
                rows.stream().mapToLong(SocieteRow::ventesActives).sum(),
                rows.stream().mapToLong(SocieteRow::ventesStallees).sum(),
                sum(rows, SocieteRow::encaisseTotal),
                sum(rows, SocieteRow::aEncaisser),
                sum(rows, SocieteRow::enRetardMontant),
                rows.stream().mapToLong(SocieteRow::enRetardCount).sum(),
                rows.stream().mapToLong(SocieteRow::optionsActives).sum(),
                rows.stream().mapToLong(SocieteRow::retractationsEnCours).sum());
    }

    private static BigDecimal sum(List<SocieteRow> rows,
                                  java.util.function.Function<SocieteRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
