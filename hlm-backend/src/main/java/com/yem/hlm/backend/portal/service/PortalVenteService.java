package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.portal.api.dto.PortalVenteResponse;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.vente.domain.Vente;
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
    private final VenteService venteService;

    public PortalVenteService(VenteRepository venteRepository, VenteService venteService) {
        this.venteRepository = venteRepository;
        this.venteService    = venteService;
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
        UUID societeId = requireSocieteId();
        UUID contactId = getContactId();

        Vente vente = venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!vente.getContact().getId().equals(contactId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND); // no info leak
        }

        return toPortalResponse(vente);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PortalVenteResponse toPortalResponse(Vente v) {
        var fullResponse = venteService.toResponse(v);
        return new PortalVenteResponse(
                v.getId(), v.getPropertyId(), v.getStatut(), v.getPrixVente(),
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
