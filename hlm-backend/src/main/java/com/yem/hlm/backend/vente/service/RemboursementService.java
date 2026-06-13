package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.common.event.VenteAnnuleeEvent;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.domain.MoyenRemboursement;
import com.yem.hlm.backend.vente.domain.Remboursement;
import com.yem.hlm.backend.vente.domain.StatutRemboursement;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.RemboursementRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks the refund of the deposit after a vente is cancelled (finding #028).
 *
 * <p>On cancellation a {@code DU} record is created automatically (via {@link VenteAnnuleeEvent})
 * so the obligation can never be silently forgotten — Nadia's point that the manager guide
 * promises a refund the platform didn't track. A gestionnaire then confirms the amount and marks
 * it {@code EFFECTUE} with the date and moyen, which is audited.
 */
@Service
public class RemboursementService {

    private static final Logger log = LoggerFactory.getLogger(RemboursementService.class);

    private final RemboursementRepository remboursementRepository;
    private final VenteRepository venteRepository;
    private final CommercialAuditService auditService;
    private final SocieteContextHelper societeCtx;

    public RemboursementService(RemboursementRepository remboursementRepository,
                                VenteRepository venteRepository,
                                CommercialAuditService auditService,
                                SocieteContextHelper societeCtx) {
        this.remboursementRepository = remboursementRepository;
        this.venteRepository = venteRepository;
        this.auditService = auditService;
        this.societeCtx = societeCtx;
    }

    // ── Auto-creation on cancellation ─────────────────────────────────────────

    /**
     * Creates the {@code DU} refund record when a vente is cancelled. Runs synchronously inside
     * the cancellation transaction; idempotent (no-op if a record already exists).
     */
    @EventListener
    @Transactional
    public void onVenteAnnulee(VenteAnnuleeEvent event) {
        if (remboursementRepository.existsByVenteId(event.getVenteId())) {
            return;
        }
        BigDecimal montant = event.getMontantDepot() != null ? event.getMontantDepot() : BigDecimal.ZERO;
        Remboursement remb = new Remboursement(
                event.getSocieteId(), event.getVenteId(), montant,
                "Remboursement du dépôt suite à annulation/rétractation", event.getActorUserId());
        remboursementRepository.save(remb);
        auditService.record(event.getSocieteId(), AuditEventType.REMBOURSEMENT_DU,
                event.getActorUserId(), "VENTE", event.getVenteId(),
                "{\"montant\":" + montant.toPlainString() + "}");
        log.info("Refund DU created for vente {} (montant {})", event.getVenteId(), montant);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Remboursement get(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        return remboursementRepository.findBySocieteIdAndVenteId(societeId, venteId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                        "Aucun remboursement pour cette vente."));
    }

    // ── Write ───────────────────────────────────────────────────────────────

    /**
     * Creates or adjusts the {@code DU} refund amount/motif for a cancelled vente. Lets a
     * gestionnaire correct the auto-filled amount (or record one for legacy annulations).
     */
    @Transactional
    public Remboursement upsertDu(UUID venteId, BigDecimal montant, String motif) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND, "Vente introuvable."));
        if (vente.getStatut() != VenteStatut.ANNULE) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST,
                    "Un remboursement ne concerne qu'une vente annulée.");
        }
        if (montant == null || montant.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Le montant doit être positif.");
        }
        Remboursement remb = remboursementRepository.findBySocieteIdAndVenteId(societeId, venteId)
                .orElseGet(() -> new Remboursement(societeId, venteId, montant, motif, societeCtx.requireUserId()));
        if (remb.getStatut() == StatutRemboursement.EFFECTUE) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST,
                    "Ce remboursement est déjà effectué.");
        }
        remb.setMontant(montant);
        if (motif != null) remb.setMotif(motif);
        remb.touch();
        return remboursementRepository.save(remb);
    }

    /** Marks the refund as paid (statut EFFECTUE) with its date, moyen and reference. */
    @Transactional
    public Remboursement marquerEffectue(UUID venteId, LocalDate date,
                                         MoyenRemboursement moyen, String reference) {
        UUID societeId = societeCtx.requireSocieteId();
        Remboursement remb = remboursementRepository.findBySocieteIdAndVenteId(societeId, venteId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                        "Aucun remboursement à confirmer pour cette vente."));
        if (remb.getStatut() == StatutRemboursement.EFFECTUE) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST, "Remboursement déjà effectué.");
        }
        if (moyen == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Le moyen de remboursement est requis.");
        }
        remb.setStatut(StatutRemboursement.EFFECTUE);
        remb.setDateRemboursement(date != null ? date : LocalDate.now());
        remb.setMoyen(moyen);
        remb.setReference(reference);
        remb.touch();
        Remboursement saved = remboursementRepository.save(remb);
        auditService.record(societeId, AuditEventType.REMBOURSEMENT_EFFECTUE,
                societeCtx.requireUserId(), "VENTE", venteId,
                "{\"montant\":" + saved.getMontant().toPlainString()
                        + ",\"moyen\":\"" + moyen.name() + "\"}");
        return saved;
    }
}
