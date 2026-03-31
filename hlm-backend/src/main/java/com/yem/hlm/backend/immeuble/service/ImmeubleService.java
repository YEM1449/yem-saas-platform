package com.yem.hlm.backend.immeuble.service;

import com.yem.hlm.backend.immeuble.api.dto.CreateImmeubleRequest;
import com.yem.hlm.backend.immeuble.api.dto.ImmeubleResponse;
import com.yem.hlm.backend.immeuble.api.dto.UpdateImmeubleRequest;
import com.yem.hlm.backend.immeuble.domain.Immeuble;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.project.service.ProjectActiveGuard;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ImmeubleService {

    private final ImmeubleRepository immeubleRepository;
    private final ProjectActiveGuard projectActiveGuard;

    public ImmeubleService(ImmeubleRepository immeubleRepository,
                           ProjectActiveGuard projectActiveGuard) {
        this.immeubleRepository = immeubleRepository;
        this.projectActiveGuard = projectActiveGuard;
    }

    @Transactional
    public ImmeubleResponse create(CreateImmeubleRequest req) {
        UUID societeId = requireSocieteId();
        var project = projectActiveGuard.requireActive(societeId, req.projectId());

        if (immeubleRepository.existsBySocieteIdAndProjectIdAndNom(societeId, req.projectId(), req.nom())) {
            throw new ImmeubleNameExistsException(req.nom(), req.projectId());
        }

        var immeuble = new Immeuble(societeId, project, req.nom());
        immeuble.setAdresse(req.adresse());
        immeuble.setNbEtages(req.nbEtages());
        immeuble.setDescription(req.description());

        return ImmeubleResponse.from(immeubleRepository.save(immeuble));
    }

    public ImmeubleResponse getById(UUID id) {
        UUID societeId = requireSocieteId();
        var immeuble = immeubleRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ImmeubleNotFoundException(id));
        return ImmeubleResponse.from(immeuble);
    }

    public List<ImmeubleResponse> list(UUID projectId) {
        UUID societeId = requireSocieteId();
        List<Immeuble> immeubles = (projectId != null)
                ? immeubleRepository.findBySocieteIdAndProjectIdOrderByNomAsc(societeId, projectId)
                : immeubleRepository.findBySocieteIdOrderByNomAsc(societeId);
        return immeubles.stream().map(ImmeubleResponse::from).toList();
    }

    @Transactional
    public ImmeubleResponse update(UUID id, UpdateImmeubleRequest req) {
        UUID societeId = requireSocieteId();
        var immeuble = immeubleRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ImmeubleNotFoundException(id));

        if (req.nom() != null) immeuble.setNom(req.nom());
        if (req.adresse() != null) immeuble.setAdresse(req.adresse());
        if (req.nbEtages() != null) immeuble.setNbEtages(req.nbEtages());
        if (req.description() != null) immeuble.setDescription(req.description());

        return ImmeubleResponse.from(immeubleRepository.save(immeuble));
    }

    @Transactional
    public void delete(UUID id) {
        UUID societeId = requireSocieteId();
        var immeuble = immeubleRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ImmeubleNotFoundException(id));
        immeubleRepository.delete(immeuble);
    }

    private UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) {
            throw new IllegalStateException("Missing société context");
        }
        return societeId;
    }
}
