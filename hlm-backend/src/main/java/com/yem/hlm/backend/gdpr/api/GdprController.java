package com.yem.hlm.backend.gdpr.api;

import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse;
import com.yem.hlm.backend.gdpr.api.dto.RectifyContactResponse;
import com.yem.hlm.backend.gdpr.service.GdprService;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GDPR / Law 09-08 Data Subject Rights API.
 *
 * <p>All endpoints are tenant-scoped via JWT {@code tid} claim.
 *
 * <ul>
 *   <li>{@code GET /api/gdpr/contacts/{contactId}/export} — Art. 15 / Art. 20 data portability</li>
 *   <li>{@code DELETE /api/gdpr/contacts/{contactId}/anonymize} — Art. 17 right to erasure</li>
 *   <li>{@code GET /api/gdpr/contacts/{contactId}/rectify} — Art. 16 rectification view</li>
 *   <li>{@code GET /api/gdpr/privacy-notice} — Art. 13 / Law 09-08 Art. 5 transparency notice</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gdpr")
public class GdprController {

    private final GdprService gdprService;
    private final PrivacyNoticeLoader privacyNoticeLoader;
    private final SocieteRepository societeRepository;

    public GdprController(GdprService gdprService,
                          PrivacyNoticeLoader privacyNoticeLoader,
                          SocieteRepository societeRepository) {
        this.gdprService = gdprService;
        this.privacyNoticeLoader = privacyNoticeLoader;
        this.societeRepository = societeRepository;
    }

    /**
     * GDPR Art. 15 / Art. 20 — Data portability export.
     * Returns all personal data held for one contact in machine-readable JSON.
     * <p>
     * RBAC: ADMIN, MANAGER only.
     */
    @GetMapping("/contacts/{contactId}/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<DataExportResponse> exportContact(@PathVariable UUID contactId) {
        return ResponseEntity.ok(gdprService.exportContact(contactId));
    }

    /**
     * GDPR Art. 17 — Right to erasure.
     * Anonymizes all PII for the contact. Blocked when SIGNED contracts exist.
     * <p>
     * RBAC: ADMIN only.
     */
    @DeleteMapping("/contacts/{contactId}/anonymize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> anonymizeContact(@PathVariable UUID contactId) {
        gdprService.anonymizeContact(contactId);
        return ResponseEntity.ok().build();
    }

    /**
     * GDPR Art. 16 — Rectification view.
     * Returns current mutable personal fields. Actual update uses PATCH /api/contacts/{id}.
     * <p>
     * RBAC: ADMIN, MANAGER.
     */
    @GetMapping("/contacts/{contactId}/rectify")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<RectifyContactResponse> getRectifyView(@PathVariable UUID contactId) {
        return ResponseEntity.ok(gdprService.getRectifyView(contactId));
    }

    /**
     * GDPR Art. 13 / Law 09-08 Art. 5 — Privacy notice.
     * Returns the operator's privacy notice text so the frontend can display it when creating contacts.
     * <p>
     * RBAC: all CRM roles (ADMIN, MANAGER, AGENT).
     */
    @GetMapping("/privacy-notice")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public ResponseEntity<PrivacyNoticeResponse> getPrivacyNotice() {
        PrivacyNoticeResponse template = privacyNoticeLoader.load();
        String text = template.text();
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId != null) {
            Societe s = societeRepository.findById(societeId).orElse(null);
            if (s != null) {
                text = text.replace("[NOM DE LA SOCIÉTÉ]",
                        s.getNom() != null ? s.getNom() : "[NOM DE LA SOCIÉTÉ]");
                text = text.replace("[EMAIL DPO]",
                        s.getEmailDpo() != null ? s.getEmailDpo() : "[EMAIL DPO]");
            }
        }
        return ResponseEntity.ok(new PrivacyNoticeResponse(template.version(), template.lastUpdated(), text));
    }

    public record PrivacyNoticeResponse(String version, String lastUpdated, String text) {}
}
