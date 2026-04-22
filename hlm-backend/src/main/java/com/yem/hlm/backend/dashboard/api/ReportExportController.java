package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.service.ReportExportService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "PDF and CSV report export")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ReportExportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv");

    private final ReportExportService svc;
    private final SocieteContextHelper ctx;

    public ReportExportController(ReportExportService svc, SocieteContextHelper ctx) {
        this.svc = svc;
        this.ctx = ctx;
    }

    @GetMapping("/ventes/pdf")
    public ResponseEntity<byte[]> ventesPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) VenteStatut statut) {

        byte[] pdf = svc.ventesPdf(ctx.requireSocieteId(), from, to, statut);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport_ventes_" + LocalDate.now() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/ventes/csv")
    public ResponseEntity<byte[]> ventesCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) VenteStatut statut) {

        byte[] csv = svc.ventesCsv(ctx.requireSocieteId(), from, to, statut);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport_ventes_" + LocalDate.now() + ".csv\"")
                .body(csv);
    }

    @GetMapping("/agents/pdf")
    public ResponseEntity<byte[]> agentsPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] pdf = svc.agentsPdf(ctx.requireSocieteId(), from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport_agents_" + LocalDate.now() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/agents/csv")
    public ResponseEntity<byte[]> agentsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = svc.agentsCsv(ctx.requireSocieteId(), from, to);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport_agents_" + LocalDate.now() + ".csv\"")
                .body(csv);
    }
}
