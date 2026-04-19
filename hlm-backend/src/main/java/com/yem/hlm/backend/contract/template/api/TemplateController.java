package com.yem.hlm.backend.contract.template.api;

import com.yem.hlm.backend.contract.template.domain.ContractTemplate;
import com.yem.hlm.backend.contract.template.domain.TemplateType;
import com.yem.hlm.backend.contract.template.service.ContractTemplateService;
import com.yem.hlm.backend.media.service.MediaTooLargeException;
import com.yem.hlm.backend.media.service.MediaTypeNotAllowedException;
import com.yem.hlm.backend.societe.SocieteContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages per-société PDF document templates.
 *
 * <ul>
 *   <li>{@code GET  /api/templates}                  — list all custom templates for the société</li>
 *   <li>{@code GET  /api/templates/{type}/source}     — get HTML source (custom or built-in)</li>
 *   <li>{@code PUT  /api/templates/{type}}            — upsert custom template</li>
 *   <li>{@code DELETE /api/templates/{type}}          — revert to built-in template</li>
 *   <li>{@code POST /api/templates/{type}/preview}    — render preview PDF</li>
 * </ul>
 *
 * <p>RBAC: ADMIN only for all write operations. ADMIN/MANAGER for reads.
 */
@Tag(name = "Templates", description = "Per-société custom PDF document templates")
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final ContractTemplateService templateService;

    public TemplateController(ContractTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<TemplateSummary>> list() {
        UUID societeId = SocieteContext.getSocieteId();
        List<TemplateSummary> result = templateService.listForSociete(societeId)
                .stream()
                .map(t -> new TemplateSummary(t.getId(), t.getTemplateType(), t.getUpdatedAt()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{type}/source")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<TemplateSourceResponse> getSource(@PathVariable TemplateType type) {
        UUID societeId = SocieteContext.getSocieteId();
        String html = templateService.getHtmlSource(societeId, type);
        boolean custom = templateService.findTemplate(societeId, type).isPresent();
        return ResponseEntity.ok(new TemplateSourceResponse(type, html, custom));
    }

    @PutMapping("/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateSummary> upsert(@PathVariable TemplateType type,
                                                   @RequestBody UpsertTemplateRequest req) {
        UUID societeId = SocieteContext.getSocieteId();
        ContractTemplate saved = templateService.upsert(societeId, type, req.htmlContent());
        return ResponseEntity.ok(new TemplateSummary(saved.getId(), saved.getTemplateType(), saved.getUpdatedAt()));
    }

    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable TemplateType type) {
        UUID societeId = SocieteContext.getSocieteId();
        templateService.delete(societeId, type);
        return ResponseEntity.noContent().build();
    }

    /**
     * Renders a preview PDF using the stored custom template (or built-in if none).
     * The preview uses an empty/placeholder model — suitable for layout checks.
     */
    @GetMapping("/{type}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> preview(@PathVariable TemplateType type) {
        UUID societeId = SocieteContext.getSocieteId();
        byte[] pdf = templateService.preview(societeId, type, buildSampleVars(type));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview-" + type.name().toLowerCase() + ".pdf\"")
                .body(pdf);
    }

    /**
     * Accepts an image file and returns it as a base64 data URI.
     * The data URI is embedded directly in the template HTML so openhtmltopdf
     * can resolve it without any external URL call during PDF rendering.
     * Limit: 3 MB, JPEG/PNG/GIF/WEBP only.
     */
    @PostMapping("/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam MultipartFile file) throws IOException {
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!ALLOWED_IMAGE_TYPES.contains(ct)) {
            throw new MediaTypeNotAllowedException(ct);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new MediaTooLargeException(file.getSize(), MAX_IMAGE_BYTES);
        }
        String b64     = Base64.getEncoder().encodeToString(file.getBytes());
        String dataUri = "data:" + ct + ";base64," + b64;
        return ResponseEntity.ok(new ImageUploadResponse(dataUri));
    }

    private static final long        MAX_IMAGE_BYTES   = 3L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    // ── Sample variables for preview rendering ───────────────────────────────

    private java.util.Map<String, Object> buildSampleVars(TemplateType type) {
        // Return a minimal placeholder model so Thymeleaf doesn't throw on missing vars.
        // The actual model classes are package-private records — we use a generic Map here.
        return java.util.Map.of("model", new SampleModel());
    }

    /** Placeholder model exposed as {@code model} in the preview template context. */
    public static class SampleModel {
        public String getSocieteName()        { return "ACME Immobilier"; }
        public String getProjectName()        { return "Résidence Les Palmiers"; }
        public String getPropertyRef()        { return "APT-001"; }
        public String getPropertyTitle()      { return "Appartement T3"; }
        public String getPropertyType()       { return "APPARTEMENT"; }
        public String getAgreedPrice()        { return "1 200 000,00"; }
        public String getListPrice()          { return "1 250 000,00"; }
        public String getBuyerDisplayName()   { return "Martin Jean"; }
        public String getBuyerPhone()         { return "+212 6 00 00 00 00"; }
        public String getBuyerEmail()         { return "jean.martin@example.com"; }
        public String getBuyerAddress()       { return "123 Rue de la Paix, Casablanca"; }
        public String getBuyerIce()           { return "BE000000000"; }
        public String getBuyerTypeLabel()     { return "Personne physique"; }
        public String getAgentEmail()         { return "agent@acme.com"; }
        public String getContractRef()        { return "ABC12345"; }
        public String getContractStatus()     { return "DRAFT"; }
        public String getSignedAt()           { return null; }
        public String getCanceledAt()         { return null; }
        public String getCreatedAt()          { return "01/01/2026"; }
        public String getGeneratedAt()        { return "01/01/2026 10:00"; }
        public String getDepositReference()   { return "RES-001"; }
        public String getDepositStatus()      { return "CONFIRMED"; }
        public String getDepositAmount()      { return "50 000,00 MAD"; }
        public String getDepositDate()        { return "01/01/2026"; }
        public String getDueDate()            { return "15/01/2026"; }
        public String getConfirmedAt()        { return "02/01/2026 09:00"; }
        public String getCancelledAt()        { return null; }
        public String getNotes()              { return null; }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record TemplateSummary(UUID id, TemplateType templateType, Instant updatedAt) {}

    public record TemplateSourceResponse(TemplateType templateType, String htmlContent, boolean custom) {}

    public record UpsertTemplateRequest(String htmlContent) {}
}
