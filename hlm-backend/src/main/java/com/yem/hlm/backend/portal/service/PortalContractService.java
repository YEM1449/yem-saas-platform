package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.portal.api.dto.PortalContractResponse;
import com.yem.hlm.backend.portal.api.dto.PortalPropertyResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTenantInfoResponse;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteDocumentType;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * All portal data-access operations scoped to the authenticated buyer (contactId from JWT).
 *
 * <p>The contactId is extracted from {@code SecurityContextHolder} principal — the portal JWT
 * stores contactId as the JWT subject, so the filter sets it as the authentication principal.
 */
@Service
@Transactional(readOnly = true)
public class PortalContractService {

    private final VenteRepository         venteRepository;
    private final VenteDocumentRepository venteDocumentRepository;
    private final PropertyRepository      propertyRepository;
    private final SocieteRepository       societeRepository;

    public PortalContractService(VenteRepository venteRepository,
                                 VenteDocumentRepository venteDocumentRepository,
                                 PropertyRepository propertyRepository,
                                 SocieteRepository societeRepository) {
        this.venteRepository         = venteRepository;
        this.venteDocumentRepository = venteDocumentRepository;
        this.propertyRepository      = propertyRepository;
        this.societeRepository       = societeRepository;
    }

    // =========================================================================
    // Contracts
    // =========================================================================

    /** Returns all non-cancelled ventes where the authenticated contact is the buyer. */
    public List<PortalContractResponse> listContracts() {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();
        return venteRepository
                .findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                .stream()
                .filter(v -> v.getStatut() != VenteStatut.ANNULE)
                .map(v -> buildResponse(societeId, v))
                .toList();
    }

    // =========================================================================
    // Property
    // =========================================================================

    /**
     * Returns property details if the authenticated contact has a vente for it.
     * Throws 404 if no such vente exists.
     */
    public PortalPropertyResponse getProperty(UUID propertyId) {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();

        boolean hasVente = venteRepository
                .findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                .stream()
                .anyMatch(v -> propertyId.equals(v.getPropertyId())
                            && v.getStatut() != VenteStatut.ANNULE);
        if (!hasVente) {
            throw new PropertyNotFoundException(propertyId);
        }

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        return PortalPropertyResponse.from(property, property.getProject().getName());
    }

    // =========================================================================
    // Société info
    // =========================================================================

    /** Returns the société's display name for the portal shell header. */
    public PortalTenantInfoResponse getTenantInfo() {
        UUID societeId = requireSocieteId();
        Societe societe = societeRepository.findById(societeId)
                .orElseThrow(() -> new IllegalStateException("Société not found in context"));
        return new PortalTenantInfoResponse(societe.getNom(), null);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private PortalContractResponse buildResponse(UUID societeId, Vente v) {
        String propertyRef  = "—";
        String propertyType = "—";
        String projectName  = "—";

        if (v.getPropertyId() != null) {
            var propOpt = propertyRepository.findBySocieteIdAndId(societeId, v.getPropertyId());
            if (propOpt.isPresent()) {
                var prop = propOpt.get();
                propertyRef  = prop.getReferenceCode() != null ? prop.getReferenceCode() : "—";
                propertyType = prop.getType() != null ? prop.getType().name() : "—";
                if (prop.getProject() != null && prop.getProject().getName() != null) {
                    projectName = prop.getProject().getName();
                }
            }
        }

        UUID docId = venteDocumentRepository.findAllByVente_IdOrderByCreatedAtDesc(v.getId())
                .stream()
                .filter(d -> VenteDocumentType.CONTRAT_GENERE.equals(d.getDocumentType()))
                .map(doc -> doc.getId())
                .findFirst()
                .orElse(null);

        return PortalContractResponse.fromVente(v, propertyRef, propertyType, projectName, docId);
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new IllegalStateException("Missing société context");
        return id;
    }

    /** The portal JWT stores contactId as the JWT subject → authentication principal. */
    private UUID getContactId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
