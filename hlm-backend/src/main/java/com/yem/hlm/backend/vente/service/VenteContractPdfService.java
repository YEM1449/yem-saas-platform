package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contract.template.domain.TemplateType;
import com.yem.hlm.backend.contract.template.service.ContractTemplateService;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VenteContractPdfService {

    private final VenteRepository            venteRepository;
    private final PropertyRepository         propertyRepository;
    private final SocieteRepository          societeRepository;
    private final DocumentGenerationService  documentGenerationService;
    private final ContractTemplateService    templateService;

    public VenteContractPdfService(
            VenteRepository venteRepository,
            PropertyRepository propertyRepository,
            SocieteRepository societeRepository,
            DocumentGenerationService documentGenerationService,
            ContractTemplateService templateService) {
        this.venteRepository           = venteRepository;
        this.propertyRepository        = propertyRepository;
        this.societeRepository         = societeRepository;
        this.documentGenerationService = documentGenerationService;
        this.templateService           = templateService;
    }

    /**
     * Generates PDF bytes for the given vente.
     * Falls back to the built-in vente-contract Thymeleaf template when the société
     * has no custom CONTRACT template defined.
     */
    public byte[] generate(UUID venteId) {
        UUID societeId = SocieteContext.getSocieteId();

        Vente vente = venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));

        String societeName = societeRepository.findById(societeId)
                .map(Societe::getNom).orElse("—");

        Property property = propertyRepository
                .findBySocieteIdAndId(societeId, vente.getPropertyId())
                .orElse(null);

        VenteContractModel model = buildModel(vente, property, societeName);
        Map<String, Object> vars = Map.of("model", model);

        return templateService.resolveCustomHtml(societeId, TemplateType.CONTRACT)
                .map(html -> documentGenerationService.renderToPdfFromString(html, vars))
                .orElseGet(() -> documentGenerationService.renderToPdf("documents/vente-contract", vars));
    }

    private VenteContractModel buildModel(Vente v, Property property, String societeName) {
        var contact = v.getContact();
        var agent   = v.getAgent();

        return new VenteContractModel(
                nvl(societeName, "—"),
                nvl(v.getVenteRef(), v.getId().toString().substring(0, 8).toUpperCase()),
                property != null ? blankToNull(property.getReferenceCode()) : null,
                property != null ? blankToNull(property.getTitle())         : null,
                property != null ? property.getType().name()               : null,
                v.getPrixVente() != null ? v.getPrixVente().toPlainString() : "—",
                contact.getFullName(),
                blankToNull(contact.getPhone()),
                blankToNull(contact.getEmail()),
                blankToNull(contact.getAddress()),
                blankToNull(contact.getNationalId()),
                agent.getPrenom() + " " + agent.getNomFamille(),
                agent.getEmail(),
                v.getDateCompromis()       != null
                        ? v.getDateCompromis().format(VenteContractModel.DATE_FMT) : "—",
                v.getDateActeNotarie()     != null
                        ? v.getDateActeNotarie().format(VenteContractModel.DATE_FMT) : null,
                v.getDateLivraisonPrevue() != null
                        ? v.getDateLivraisonPrevue().format(VenteContractModel.DATE_FMT) : null,
                v.getStatut().name(),
                v.getContractStatus().name(),
                LocalDateTime.now().format(VenteContractModel.DATETIME_FMT));
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
