package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.vente.api.dto.GeneratedDocumentResponse;
import com.yem.hlm.backend.vente.domain.ReserveLivraison;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteDocument;
import com.yem.hlm.backend.vente.domain.VenteDocumentType;
import com.yem.hlm.backend.vente.repo.ReserveLivraisonRepository;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates Loi 44-00 legal documents (réservation contract, PV de livraison) as PDFs,
 * stores them, and saves a {@link VenteDocument} so they surface in the CRM and the buyer
 * portal through the existing document endpoints.
 */
@Service
@Transactional
public class VenteLegalDocumentService {

    private final VenteRepository venteRepository;
    private final PropertyRepository propertyRepository;
    private final SocieteRepository societeRepository;
    private final ReserveLivraisonRepository reserveRepository;
    private final VenteDocumentRepository documentRepository;
    private final DocumentGenerationService pdf;
    private final MediaStorageService storage;
    private final SocieteContextHelper societeCtx;

    public VenteLegalDocumentService(VenteRepository venteRepository,
                                     PropertyRepository propertyRepository,
                                     SocieteRepository societeRepository,
                                     ReserveLivraisonRepository reserveRepository,
                                     VenteDocumentRepository documentRepository,
                                     DocumentGenerationService pdf,
                                     MediaStorageService storage,
                                     SocieteContextHelper societeCtx) {
        this.venteRepository = venteRepository;
        this.propertyRepository = propertyRepository;
        this.societeRepository = societeRepository;
        this.reserveRepository = reserveRepository;
        this.documentRepository = documentRepository;
        this.pdf = pdf;
        this.storage = storage;
        this.societeCtx = societeCtx;
    }

    public GeneratedDocumentResponse generateContratReservation(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);
        Property property = property(societeId, vente);

        Map<String, Object> model = new java.util.HashMap<>();
        model.put("societeName", societeName(societeId));
        model.put("acquereur", vente.getContact().getFullName());
        model.put("acquereurCin", orDash(vente.getContact().getNationalId()));
        model.put("propertyRef", property != null ? orDash(property.getReferenceCode()) : "—");
        model.put("projectName", property != null ? orDash(property.getProjectName()) : "—");
        model.put("prix", money(vente.getPrixVente()));
        model.put("depot", "—");
        model.put("venteRef", orDash(vente.getVenteRef()));
        model.put("dateFinRetractation", str(vente.getDateFinDelaiReflexion()));

        byte[] bytes = pdf.renderToPdf("documents/contrat-reservation-vefa", Map.of("model", model));
        return store(societeId, vente, bytes,
                "contrat-reservation-" + safe(vente.getVenteRef()) + ".pdf",
                VenteDocumentType.CONTRAT_RESERVATION);
    }

    public GeneratedDocumentResponse generatePvLivraison(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);
        Property property = property(societeId, vente);

        List<Map<String, Object>> reserves = new ArrayList<>();
        for (ReserveLivraison r : reserveRepository.findBySocieteIdAndVenteIdOrderByDateConstatAsc(societeId, venteId)) {
            reserves.add(Map.of(
                    "description", orDash(r.getDescription()),
                    "statut", r.getStatut() != null ? r.getStatut().name() : "—",
                    "dateLeveePrevue", str(r.getDateLeveePrevue())));
        }

        Map<String, Object> model = new java.util.HashMap<>();
        model.put("societeName", societeName(societeId));
        model.put("acquereur", vente.getContact().getFullName());
        model.put("propertyRef", property != null ? orDash(property.getReferenceCode()) : "—");
        model.put("venteRef", orDash(vente.getVenteRef()));
        model.put("dateLivraison", str(vente.getDateLivraisonReelle() != null
                ? vente.getDateLivraisonReelle() : LocalDate.now()));
        model.put("reserves", reserves);

        byte[] bytes = pdf.renderToPdf("documents/pv-livraison-vefa", Map.of("model", model));
        return store(societeId, vente, bytes,
                "pv-livraison-" + safe(vente.getVenteRef()) + ".pdf",
                VenteDocumentType.PV_LIVRAISON);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GeneratedDocumentResponse store(UUID societeId, Vente vente, byte[] bytes,
                                            String fileName, VenteDocumentType type) {
        String key;
        try {
            key = storage.store(bytes, fileName, "application/pdf");
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du stockage du document légal", e);
        }
        VenteDocument doc = new VenteDocument(societeId, vente, fileName, key,
                "application/pdf", (long) bytes.length, type);
        documentRepository.save(doc);
        return new GeneratedDocumentResponse(doc.getId(), fileName, type);
    }

    private Vente requireVente(UUID societeId, UUID venteId) {
        return venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));
    }

    private Property property(UUID societeId, Vente vente) {
        return propertyRepository.findBySocieteIdAndId(societeId, vente.getPropertyId()).orElse(null);
    }

    private String societeName(UUID societeId) {
        return societeRepository.findById(societeId).map(Societe::getNom).orElse("—");
    }

    private static String orDash(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String str(LocalDate d) { return d == null ? "—" : d.toString(); }
    private static String safe(String s) { return s == null ? "doc" : s.replaceAll("[^A-Za-z0-9_-]", "_"); }
    private static String money(java.math.BigDecimal v) { return v == null ? "—" : v.toPlainString() + " MAD"; }
}
