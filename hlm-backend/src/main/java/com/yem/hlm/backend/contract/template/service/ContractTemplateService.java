package com.yem.hlm.backend.contract.template.service;

import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.contract.template.domain.ContractTemplate;
import com.yem.hlm.backend.contract.template.domain.TemplateType;
import com.yem.hlm.backend.contract.template.repo.ContractTemplateRepository;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages per-société custom PDF templates and delegates PDF preview rendering.
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>Société-specific template stored in {@code contract_template} table.</li>
 *   <li>Built-in classpath Thymeleaf template ({@code templates/documents/<type>.html}).</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class ContractTemplateService {

    private static final Map<TemplateType, String> CLASSPATH_TEMPLATES = Map.of(
            TemplateType.CONTRACT,       "documents/contract",
            TemplateType.RESERVATION,    "documents/reservation",
            TemplateType.CALL_FOR_FUNDS, "documents/appel-de-fonds"
    );

    private final ContractTemplateRepository templateRepo;
    private final DocumentGenerationService  docService;

    public ContractTemplateService(ContractTemplateRepository templateRepo,
                                   DocumentGenerationService docService) {
        this.templateRepo = templateRepo;
        this.docService   = docService;
    }

    // =========================================================================
    // Query
    // =========================================================================

    public List<ContractTemplate> listForSociete(UUID societeId) {
        return templateRepo.findBySocieteId(societeId);
    }

    public Optional<ContractTemplate> findTemplate(UUID societeId, TemplateType type) {
        return templateRepo.findBySocieteIdAndTemplateType(societeId, type);
    }

    /**
     * Returns the custom HTML string for the given type, or {@link Optional#empty()} if none set.
     * Callers use this to decide whether to call
     * {@link DocumentGenerationService#renderToPdfFromString} or
     * {@link DocumentGenerationService#renderToPdf}.
     */
    public Optional<String> resolveCustomHtml(UUID societeId, TemplateType type) {
        return templateRepo.findBySocieteIdAndTemplateType(societeId, type)
                .map(ContractTemplate::getHtmlContent);
    }

    // =========================================================================
    // Write
    // =========================================================================

    @Transactional
    public ContractTemplate upsert(UUID societeId, TemplateType type, String htmlContent) {
        ContractTemplate tpl = templateRepo.findBySocieteIdAndTemplateType(societeId, type)
                .orElseGet(() -> new ContractTemplate(societeId, type, htmlContent));
        tpl.setHtmlContent(htmlContent);
        return templateRepo.save(tpl);
    }

    @Transactional
    public void delete(UUID societeId, TemplateType type) {
        if (!templateRepo.existsBySocieteIdAndTemplateType(societeId, type)) {
            throw new BusinessRuleException(ErrorCode.NOT_FOUND,
                    "Aucun modèle personnalisé trouvé pour le type " + type);
        }
        templateRepo.deleteBySocieteIdAndTemplateType(societeId, type);
    }

    // =========================================================================
    // Preview
    // =========================================================================

    /**
     * Renders a PDF preview using {@code sampleHtml} (if provided) or the stored/classpath template,
     * populated with {@code variables}.
     *
     * @param societeId  société owning the template
     * @param type       template type
     * @param variables  Thymeleaf model variables
     * @return PDF bytes
     */
    public byte[] preview(UUID societeId, TemplateType type, Map<String, Object> variables) {
        Optional<String> custom = resolveCustomHtml(societeId, type);
        if (custom.isPresent()) {
            return docService.renderToPdfFromString(custom.get(), variables);
        }
        String classpathName = CLASSPATH_TEMPLATES.get(type);
        return docService.renderToPdf(classpathName, variables);
    }

    /**
     * Returns the raw HTML source for a template — custom DB version if present,
     * otherwise the built-in classpath file (for display in the editor).
     */
    public String getHtmlSource(UUID societeId, TemplateType type) {
        Optional<ContractTemplate> custom = findTemplate(societeId, type);
        if (custom.isPresent()) {
            return custom.get().getHtmlContent();
        }
        // Return built-in classpath template as starting point
        String resourcePath = "templates/" + CLASSPATH_TEMPLATES.get(type) + ".html";
        ClassPathResource res = new ClassPathResource(resourcePath);
        if (res.exists()) {
            try {
                return res.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BusinessRuleException(ErrorCode.INTERNAL_ERROR,
                        "Impossible de lire le modèle intégré: " + resourcePath);
            }
        }
        return "";
    }

}
