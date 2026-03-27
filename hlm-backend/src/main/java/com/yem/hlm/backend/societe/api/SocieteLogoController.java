package com.yem.hlm.backend.societe.api;

import com.yem.hlm.backend.societe.service.SocieteLogoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * Exposes société logo download to all authenticated CRM roles (ADMIN, MANAGER, AGENT).
 * Upload/delete is SUPER_ADMIN only — see SocieteController.
 * SecurityConfig permits GET /api/societes/{id}/logo for all CRM roles.
 */
@Tag(name = "Societe", description = "Société branding — accessible to all CRM roles")
@RestController
@RequestMapping("/api/societes")
public class SocieteLogoController {

    private final SocieteLogoService societeLogoService;

    public SocieteLogoController(SocieteLogoService societeLogoService) {
        this.societeLogoService = societeLogoService;
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<InputStreamResource> downloadLogo(@PathVariable UUID id) throws IOException {
        String contentType = societeLogoService.getLogoContentType(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(new InputStreamResource(societeLogoService.downloadLogo(id)));
    }
}
