package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.api.dto.DossierFinancementResponse;
import com.yem.hlm.backend.vente.api.dto.UpsertDossierFinancementRequest;
import com.yem.hlm.backend.vente.domain.DossierFinancement;
import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import com.yem.hlm.backend.vente.repo.DossierFinancementRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manages the financing file (dossier de financement) of a vente — one per vente, with a
 * multi-status workflow. Société- and vente-scoped.
 */
@Service
@Transactional
public class DossierFinancementService {

    private final DossierFinancementRepository repo;
    private final VenteRepository venteRepository;
    private final SocieteContextHelper societeCtx;

    public DossierFinancementService(DossierFinancementRepository repo,
                                     VenteRepository venteRepository,
                                     SocieteContextHelper societeCtx) {
        this.repo = repo;
        this.venteRepository = venteRepository;
        this.societeCtx = societeCtx;
    }

    @Transactional(readOnly = true)
    public DossierFinancementResponse getByVente(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        return repo.findBySocieteIdAndVenteId(societeId, venteId)
                .map(DossierFinancementResponse::from)
                .orElseThrow(() -> new DossierFinancementNotFoundException(venteId));
    }

    /** Creates the financing file on first call, updates it afterwards (1:1 with the vente). */
    public DossierFinancementResponse upsert(UUID venteId, UpsertDossierFinancementRequest req) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId);

        DossierFinancement d = repo.findBySocieteIdAndVenteId(societeId, venteId)
                .orElseGet(() -> new DossierFinancement(societeId, venteId));

        d.setTypeFinancement(req.typeFinancement());
        d.setBanque(req.banque());
        d.setMontantCredit(req.montantCredit());
        d.setTauxInteret(req.tauxInteret());
        d.setDureeMois(req.dureeMois());
        d.setApportPersonnel(req.apportPersonnel());
        if (req.statut() != null) d.setStatut(req.statut());
        d.setDateDemande(req.dateDemande());
        d.setDateAccord(req.dateAccord());
        d.setDateExpirationAccord(req.dateExpirationAccord());
        d.setCommentaire(req.commentaire());

        return DossierFinancementResponse.from(repo.save(d));
    }

    /**
     * Financing files whose granted-agreement deadline expires within {@code days} and that are
     * not yet definitively approved/refused — feeds the alert dashboard (P6). System context.
     */
    @Transactional(readOnly = true)
    public List<DossierFinancement> findAccordsExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        return repo.findByStatutInAndDateExpirationAccordBetween(
                List.of(StatutDossierFinancement.EN_COURS, StatutDossierFinancement.ACCORD_PRINCIPE),
                today, today.plusDays(days));
    }

    private void requireVente(UUID societeId, UUID venteId) {
        venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));
    }
}
