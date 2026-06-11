package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.vente.api.dto.*;
import com.yem.hlm.backend.vente.service.VenteContractPdfService;
import com.yem.hlm.backend.vente.service.VenteInviteService;
import com.yem.hlm.backend.vente.service.VenteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
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
    private final VenteContractPdfService venteContractPdfService;
    private final com.yem.hlm.backend.vente.service.VenteLegalDocumentService legalDocumentService;

    public VenteController(VenteService venteService,
                           VenteInviteService venteInviteService,
                           MediaStorageService mediaStorage,
                           VenteContractPdfService venteContractPdfService,
                           com.yem.hlm.backend.vente.service.VenteLegalDocumentService legalDocumentService) {
        this.venteService             = venteService;
        this.venteInviteService       = venteInviteService;
        this.mediaStorage             = mediaStorage;
        this.venteContractPdfService  = venteContractPdfService;
        this.legalDocumentService     = legalDocumentService;
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

    // ── VEFA Loi 44-00 — OPTION + rétractation ───────────────────────────────

    @PostMapping("/option")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse createOption(@Valid @RequestBody CreateOptionRequest request) {
        return venteService.createOption(request.propertyId(), request.contactId(), request.dureeHeures());
    }

    @PostMapping("/{id}/confirm-reservation")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public VenteResponse confirmReservation(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmReservationRequest request) {
        return venteService.confirmReservation(id, request.montantDepot());
    }

    @PostMapping("/{id}/retractation")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public VenteResponse exerciseRetractation(@PathVariable UUID id) {
        return venteService.exerciseRetractation(id);
    }

    // ── VEFA — Livraison avec réserves ───────────────────────────────────────

    @PostMapping("/{id}/livraison")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public VenteResponse recordDelivery(
            @PathVariable UUID id,
            @Valid @RequestBody RecordDeliveryRequest request) {
        return venteService.recordDelivery(id, request);
    }

    // ── VEFA legal document generation (Loi 44-00) ───────────────────────────

    @PostMapping("/{id}/documents/contrat-reservation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public GeneratedDocumentResponse generateContratReservation(@PathVariable UUID id) {
        return legalDocumentService.generateContratReservation(id);
    }

    @PostMapping("/{id}/echeances/{echeanceId}/quittance")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public GeneratedDocumentResponse generateQuittance(@PathVariable UUID id, @PathVariable UUID echeanceId) {
        return legalDocumentService.generateQuittance(id, echeanceId);
    }

    @PostMapping("/{id}/documents/pv-livraison")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public GeneratedDocumentResponse generatePvLivraison(@PathVariable UUID id) {
        return legalDocumentService.generatePvLivraison(id);
    }

    @GetMapping("/{id}/reserves")
    public List<ReserveLivraisonResponse> listReserves(@PathVariable UUID id) {
        return venteService.listReserves(id);
    }

    @PutMapping("/{id}/reserves/{reserveId}/lever")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public VenteResponse liftReserve(@PathVariable UUID id, @PathVariable UUID reserveId) {
        return venteService.liftReserve(id, reserveId);
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

    /** Generates the legal VEFA call-for-funds schedule (Art. 618-17 Loi 44-00). */
    @PostMapping("/{id}/echeancier/generer-legal")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<EcheanceResponse> generateEcheancierLegal(@PathVariable UUID id) {
        return venteService.generateEcheancierLegal(id);
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
        try {
            byte[] pdfBytes  = venteContractPdfService.generate(id);
            String fileName  = "contrat_" + id.toString().substring(0, 8).toUpperCase() + ".pdf";
            String storageKey = mediaStorage.store(pdfBytes, fileName, "application/pdf");
            return venteService.generateContract(id, storageKey, fileName, (long) pdfBytes.length);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de la génération du contrat : " + e.getMessage());
        }
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

    /** Download a previously uploaded document (streams the file from object storage). */
    @GetMapping("/{id}/documents/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        String key = venteService.downloadDocumentKey(id, docId);
        try {
            InputStream stream = mediaStorage.load(key);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load document: " + e.getMessage());
        }
    }
}
