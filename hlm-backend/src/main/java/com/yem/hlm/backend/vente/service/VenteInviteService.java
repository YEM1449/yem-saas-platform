package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.portal.service.PortalAuthService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Sends a portal magic-link invitation to the buyer of a given Vente.
 *
 * <p>Delegates email + token creation to the existing {@link PortalAuthService}
 * to avoid duplicating the magic-link logic.
 */
@Service
@Transactional
public class VenteInviteService {

    private final VenteRepository venteRepository;
    private final ContactRepository contactRepository;
    private final SocieteRepository societeRepository;
    private final PortalAuthService portalAuthService;
    private final SocieteContextHelper societeCtx;

    public VenteInviteService(VenteRepository venteRepository,
                              ContactRepository contactRepository,
                              SocieteRepository societeRepository,
                              PortalAuthService portalAuthService,
                              SocieteContextHelper societeCtx) {
        this.venteRepository   = venteRepository;
        this.contactRepository = contactRepository;
        this.societeRepository = societeRepository;
        this.portalAuthService = portalAuthService;
        this.societeCtx        = societeCtx;
    }

    /**
     * Generates a portal magic link for the buyer of the given vente and sends it by email.
     *
     * @param venteId the vente whose contact should receive the invitation
     * @return magic-link response (URL included only in dev/test; empty in production)
     */
    public MagicLinkResponse inviteBuyer(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();

        var vente = venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));

        var contact = contactRepository.findBySocieteIdAndId(societeId, vente.getContact().getId())
                .orElseThrow(() -> new ContactNotFoundException(vente.getContact().getId()));

        var societe = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Société not found"));

        return portalAuthService.requestLink(contact.getEmail(), societe.getKey());
    }
}
