package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.portal.api.dto.PortalVenteResponse;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.vente.api.dto.VenteDocumentResponse;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteDocumentType;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.vente.service.VenteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Portal-scoped vente data access.
 *
 * <p>Contacts can only see their own ventes — cross-contact access → 404 (no info leak).
 * The contactId is taken from the portal JWT subject (set by JwtAuthenticationFilter).
 */
@Service
@Transactional(readOnly = true)
public class PortalVenteService {

    private final VenteRepository venteRepository;
    private final VenteDocumentRepository documentRepository;
    private final VenteService venteService;
    private final MediaStorageService mediaStorage;

    public PortalVenteService(VenteRepository venteRepository,
                              VenteDocumentRepository documentRepository,
                              VenteService venteService,
                              MediaStorageService mediaStorage) {
        this.venteRepository    = venteRepository;
        this.documentRepository = documentRepository;
        this.venteService       = venteService;
        this.mediaStorage       = mediaStorage;
    }

    /** Returns all ventes for the authenticated buyer (may be empty). */
    public List<PortalVenteResponse> listMyVentes() {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();
        return venteRepository
                .findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                .stream()
                .map(this::toPortalResponse)
                .toList();
    }

    /** Returns a single vente if it belongs to the authenticated buyer. */
    public PortalVenteResponse getMyVente(UUID venteId) {
        Vente vente = requireOwnedVente(venteId);
        return toPortalResponse(vente);
    }

    /**
     * Returns the storage key for a document attached to the buyer's vente.
     * Enforces cross-contact isolation.
     */
    public String getDocumentKey(UUID venteId, UUID docId) {
        requireOwnedVente(venteId);
        UUID societeId = requireSocieteId();
        return documentRepository.findBySocieteIdAndId(societeId, docId)
                .map(d -> d.getStorageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    /** Upload a document on behalf of the buyer (sets uploadedByPortal = true). */
    @Transactional
    public VenteDocumentResponse uploadDocument(UUID venteId, String nomFichier,
                                                String storageKey, String contentType,
                                                Long tailleOctets, VenteDocumentType documentType) {
        requireOwnedVente(venteId);
        return venteService.addDocumentFromPortal(venteId, nomFichier, storageKey,
                contentType, tailleOctets, documentType);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Vente requireOwnedVente(UUID venteId) {
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();
        Vente vente = venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!vente.getContact().getId().equals(contactId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND); // no info leak
        }
        return vente;
    }

    private PortalVenteResponse toPortalResponse(Vente v) {
        var fullResponse = venteService.toResponse(v);
        return new PortalVenteResponse(
                v.getId(), v.getVenteRef(), v.getContractStatus(),
                v.getPropertyId(), v.getStatut(), v.getPrixVente(),
                v.getDateCompromis(), v.getDateActeNotarie(),
                v.getDateLivraisonPrevue(), v.getDateLivraisonReelle(),
                fullResponse.echeances(), fullResponse.documents(),
                v.getCreatedAt());
    }

    private UUID requireSocieteId() {
        UUID sid = SocieteContext.getSocieteId();
        if (sid == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing société context");
        }
        return sid;
    }

    private UUID getContactId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
