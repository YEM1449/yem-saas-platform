package com.yem.hlm.backend.tranche.service;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.api.dto.TrancheDto;
import com.yem.hlm.backend.tranche.api.dto.UpdateTrancheStatutRequest;
import com.yem.hlm.backend.tranche.domain.Tranche;
import com.yem.hlm.backend.tranche.domain.TrancheStatut;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TrancheService {

    // Allowed forward-only transitions
    private static final List<TrancheStatut> ORDERED = List.of(
            TrancheStatut.EN_PREPARATION,
            TrancheStatut.EN_COMMERCIALISATION,
            TrancheStatut.EN_TRAVAUX,
            TrancheStatut.ACHEVEE,
            TrancheStatut.LIVREE
    );

    private final TrancheRepository trancheRepo;

    public TrancheService(TrancheRepository trancheRepo) {
        this.trancheRepo = trancheRepo;
    }

    /** List all tranches for a project, enriched with KPI aggregates. */
    public List<TrancheDto> listByProject(UUID projectId) {
        UUID societeId = requireSocieteId();
        return trancheRepo
                .findBySocieteIdAndProjectIdOrderByNumeroAsc(societeId, projectId)
                .stream()
                .map(t -> enrichWithKpis(societeId, t))
                .toList();
    }

    /** Get a single tranche with KPI aggregates. */
    public TrancheDto getById(UUID projectId, UUID trancheId) {
        UUID societeId = requireSocieteId();
        UUID tid = trancheId;
        Tranche t = trancheRepo.findBySocieteIdAndId(societeId, tid)
                .orElseThrow(() -> new TrancheNotFoundException(tid));
        if (!t.getProjectId().equals(projectId)) {
            throw new TrancheNotFoundException(tid);
        }
        return enrichWithKpis(societeId, t);
    }

    /** Advance a tranche's statut (forward-only, no skipping). */
    @Transactional
    public TrancheDto advanceStatut(UUID projectId, UUID trancheId,
                                     UpdateTrancheStatutRequest req) {
        UUID societeId = requireSocieteId();
        UUID tid = trancheId;
        Tranche t = trancheRepo.findBySocieteIdAndId(societeId, tid)
                .orElseThrow(() -> new TrancheNotFoundException(tid));
        if (!t.getProjectId().equals(projectId)) {
            throw new TrancheNotFoundException(tid);
        }

        validateTransition(t.getStatut(), req.statut());
        t.setStatut(req.statut());
        t = trancheRepo.save(t);
        return enrichWithKpis(societeId, t);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private TrancheDto enrichWithKpis(UUID societeId, Tranche t) {
        int buildings  = trancheRepo.countBuildings(societeId, t.getId());
        int total      = trancheRepo.countUnits(societeId, t.getId());

        int disponibles = 0, reservees = 0, vendues = 0;
        for (Object[] row : trancheRepo.countUnitsByStatus(societeId, t.getId())) {
            PropertyStatus status = (PropertyStatus) row[0];
            int count = ((Number) row[1]).intValue();
            switch (status) {
                case ACTIVE   -> disponibles += count;
                case RESERVED -> reservees   += count;
                case SOLD     -> vendues      += count;
                default       -> { /* DRAFT/ARCHIVED not counted */ }
            }
        }

        BigDecimal taux = BigDecimal.ZERO;
        if (total > 0) {
            taux = BigDecimal.valueOf((double)(reservees + vendues) / total * 100)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new TrancheDto(
                t.getId(), t.getProjectId(),
                t.getNumero(), t.getNom(), t.getDisplayNom(),
                t.getStatut(),
                t.getDateLivraisonPrevue(), t.getDateLivraisonEff(),
                t.getDateDebutTravaux(), t.getPermisConstruireRef(),
                t.getDescription(),
                buildings, total, disponibles, reservees, vendues, taux
        );
    }

    private void validateTransition(TrancheStatut from, TrancheStatut to) {
        int fromIdx = ORDERED.indexOf(from);
        int toIdx   = ORDERED.indexOf(to);
        if (toIdx != fromIdx + 1) {
            throw new InvalidTrancheTransitionException(from, to);
        }
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new IllegalStateException("Missing société context");
        return id;
    }
}
