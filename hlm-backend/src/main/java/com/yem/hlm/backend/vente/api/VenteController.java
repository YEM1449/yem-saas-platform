package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.vente.api.dto.*;
import com.yem.hlm.backend.vente.service.VenteInviteService;
import com.yem.hlm.backend.vente.service.VenteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST API for the Vente (sale) pipeline.
 *
 * <p>All endpoints are société-scoped via {@code SocieteContext}.
 * AGENT has write access in the vente module (deliberate RBAC exception) so
 * that assigned agents can run their own deals end-to-end — from creating the
 * vente off a reservation through échéances, documents, and contract signing.
 */
@Tag(name = "Ventes", description = "Sales pipeline — from compromis to livraison")
@RestController
@RequestMapping("/api/ventes")
public class VenteController {

    private final VenteService venteService;
    private final VenteInviteService venteInviteService;
    private final MediaStorageService mediaStorage;

    public VenteController(VenteService venteService,
                           VenteInviteService venteInviteService,
                           MediaStorageService mediaStorage) {
        this.venteService       = venteService;
        this.venteInviteService = venteInviteService;
        this.mediaStorage       = mediaStorage;
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse create(@Valid @RequestBody CreateVenteRequest request) {
        return venteService.create(request);
    }

    @GetMapping
    public List<VenteResponse> list(@RequestParam(required = false) UUID contactId) {
        if (contactId != null) return venteService.findByContactId(contactId);
        return venteService.findAll();
    }

    @GetMapping("/{id}")
    public VenteResponse get(@PathVariable UUID id) {
        return venteService.findById(id);
    }

    // =========================================================================
    // Statut transition
    // =========================================================================

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse updateStatut(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVenteStatutRequest request) {
        return venteService.updateStatut(id, request);
    }

    // =========================================================================
    // Échéancier
    // =========================================================================

    @GetMapping("/{id}/echeances")
    public List<EcheanceResponse> listEcheances(@PathVariable UUID id) {
        return venteService.findEcheances(id);
    }

    @PostMapping("/{id}/echeances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public EcheanceResponse addEcheance(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEcheanceRequest request) {
        return venteService.addEcheance(id, request);
    }

    @PatchMapping("/{id}/echeances/{eid}/statut")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public EcheanceResponse updateEcheanceStatut(
            @PathVariable UUID id,
            @PathVariable UUID eid,
            @Valid @RequestBody UpdateEcheanceStatutRequest request) {
        return venteService.updateEcheanceStatut(id, eid, request);
    }

    // =========================================================================
    // Documents
    // =========================================================================

    @GetMapping("/{id}/documents")
    public List<VenteDocumentResponse> listDocuments(@PathVariable UUID id) {
        return venteService.findDocuments(id);
    }

    // =========================================================================
    // Portal invitation
    // =========================================================================

    /** Sends a portal magic-link email to the buyer of the given vente. */
    @PostMapping("/{id}/portal/invite")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public MagicLinkResponse inviteBuyer(@PathVariable UUID id) {
        return venteInviteService.inviteBuyer(id);
    }

    /** Generate the sale contract PDF and transition contractStatus → GENERATED. */
    @PostMapping("/{id}/contract/generate")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse generateContract(@PathVariable UUID id) {
        return venteService.generateContract(id);
    }

    /** Sign the contract — requires contractStatus == GENERATED. */
    @PostMapping("/{id}/contract/sign")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse signContract(@PathVariable UUID id) {
        return venteService.signContract(id);
    }

    /** Update financing information for a vente (patch semantics — null fields are ignored). */
    @PatchMapping("/{id}/financement")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse updateFinancement(
            @PathVariable UUID id,
            @RequestBody UpdateFinancingRequest request) {
        return venteService.updateFinancement(id, request);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteDocumentResponse uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        try {
            String storageKey = mediaStorage.store(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());
            return venteService.addDocument(
                    id,
                    file.getOriginalFilename(),
                    storageKey,
                    file.getContentType(),
                    file.getSize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store document: " + e.getMessage());
        }
    }
}
