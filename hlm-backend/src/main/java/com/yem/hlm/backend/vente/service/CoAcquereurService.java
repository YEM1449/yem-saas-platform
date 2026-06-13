package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.api.dto.CoAcquereurResponse;
import com.yem.hlm.backend.vente.api.dto.UpsertCoAcquereurRequest;
import com.yem.hlm.backend.vente.domain.CoAcquereur;
import com.yem.hlm.backend.vente.repo.CoAcquereurRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages co-buyers (co-acquéreurs) of a vente. Wave 12 allows one co-buyer per vente;
 * all operations are société-scoped and verify the parent vente belongs to the société.
 */
@Service
@Transactional
public class CoAcquereurService {

    private final CoAcquereurRepository repo;
    private final VenteRepository venteRepository;
    private final SocieteContextHelper societeCtx;

    public CoAcquereurService(CoAcquereurRepository repo,
                              VenteRepository venteRepository,
                              SocieteContextHelper societeCtx) {
        this.repo = repo;
        this.venteRepository = venteRepository;
        this.societeCtx = societeCtx;
    }

    @Transactional(readOnly = true)
    public List<CoAcquereurResponse> list(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId);
        return repo.findBySocieteIdAndVenteIdOrderByCreatedAtAsc(societeId, venteId)
                .stream().map(CoAcquereurResponse::from).toList();
    }

    public CoAcquereurResponse add(UUID venteId, UpsertCoAcquereurRequest req) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId);
        if (repo.existsBySocieteIdAndVenteId(societeId, venteId)) {
            throw new CoAcquereurAlreadyExistsException();
        }
        CoAcquereur c = new CoAcquereur(societeId, venteId, req.nom(), req.prenom());
        apply(c, req);
        return CoAcquereurResponse.from(repo.save(c));
    }

    public CoAcquereurResponse update(UUID venteId, UUID coId, UpsertCoAcquereurRequest req) {
        UUID societeId = societeCtx.requireSocieteId();
        CoAcquereur c = requireCoAcquereur(societeId, venteId, coId);
        c.setNom(req.nom());
        c.setPrenom(req.prenom());
        apply(c, req);
        return CoAcquereurResponse.from(repo.save(c));
    }

    public void delete(UUID venteId, UUID coId) {
        UUID societeId = societeCtx.requireSocieteId();
        repo.delete(requireCoAcquereur(societeId, venteId, coId));
    }

    private CoAcquereur requireCoAcquereur(UUID societeId, UUID venteId, UUID coId) {
        CoAcquereur c = repo.findBySocieteIdAndId(societeId, coId)
                .orElseThrow(() -> new CoAcquereurNotFoundException(coId));
        if (!c.getVenteId().equals(venteId)) {
            throw new CoAcquereurNotFoundException(coId);
        }
        return c;
    }

    private void requireVente(UUID societeId, UUID venteId) {
        venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));
    }

    /** Full-replace of editable fields (PUT semantics). */
    private void apply(CoAcquereur c, UpsertCoAcquereurRequest req) {
        c.setCinNumero(req.cinNumero());
        c.setCinDateDelivrance(req.cinDateDelivrance());
        c.setPasseportNumero(req.passeportNumero());
        c.setDateNaissance(req.dateNaissance());
        c.setNationalite(req.nationalite());
        c.setPaysResidence(req.paysResidence());
        c.setSituationMatrimoniale(req.situationMatrimoniale());
        c.setTypeAcquereur(req.typeAcquereur());
        c.setEmail(req.email());
        c.setTelephone(req.telephone());
        if (req.roleAcquereur() != null) c.setRoleAcquereur(req.roleAcquereur());
    }
}
