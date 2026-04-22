package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReportExportService {

    private final VenteRepository          venteRepo;
    private final SocieteRepository        societeRepo;
    private final DocumentGenerationService docGenService;

    public ReportExportService(VenteRepository venteRepo,
                               SocieteRepository societeRepo,
                               DocumentGenerationService docGenService) {
        this.venteRepo     = venteRepo;
        this.societeRepo   = societeRepo;
        this.docGenService = docGenService;
    }

    // ── Ventes PDF ────────────────────────────────────────────────────────────

    public byte[] ventesPdf(UUID societeId, LocalDate from, LocalDate to, VenteStatut statut) {
        String societeNom = societeRepo.findById(societeId).map(s -> s.getNom()).orElse("");
        List<Vente> ventes = fetchVentes(societeId, from, to, statut);
        BigDecimal total = ventes.stream()
                .filter(v -> v.getPrixVente() != null)
                .map(Vente::getPrixVente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> vars = Map.of(
                "ventes",       ventes,
                "societeNom",   societeNom,
                "from",         from,
                "to",           to,
                "statutFilter", statut != null ? statut.name() : null,
                "total",        total,
                "generatedAt",  LocalDateTime.now()
        );
        return docGenService.renderToPdf("reports/ventes-report", vars);
    }

    // ── Ventes CSV ────────────────────────────────────────────────────────────

    public byte[] ventesCsv(UUID societeId, LocalDate from, LocalDate to, VenteStatut statut) {
        List<Vente> ventes = fetchVentes(societeId, from, to, statut);
        StringBuilder sb = new StringBuilder();
        sb.append("Référence,Acquéreur,Statut,Prix (MAD),Date compromis,Date livraison prévue,Agent\n");
        for (Vente v : ventes) {
            sb.append(escapeCsv(v.getVenteRef()))
              .append(',').append(escapeCsv(v.getContact().getFullName()))
              .append(',').append(v.getStatut().name())
              .append(',').append(v.getPrixVente() != null ? v.getPrixVente().toPlainString() : "")
              .append(',').append(v.getDateCompromis() != null ? v.getDateCompromis().toString() : "")
              .append(',').append(v.getDateLivraisonPrevue() != null ? v.getDateLivraisonPrevue().toString() : "")
              .append(',').append(escapeCsv(agentName(v)))
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Agents PDF ────────────────────────────────────────────────────────────

    public byte[] agentsPdf(UUID societeId, LocalDate from, LocalDate to) {
        String societeNom = societeRepo.findById(societeId).map(s -> s.getNom()).orElse("");
        LocalDateTime fromDt = from != null ? from.atStartOfDay()   : null;
        LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX) : null;

        List<Object[]> rows = venteRepo.agentsLeaderboardForReport(societeId, fromDt, toDt);
        List<Map<String, Object>> agents = rows.stream().map(r -> Map.<String, Object>of(
                "agentId",     r[0],
                "agentName",   r[1] + " " + r[2],
                "totalCA",     r[3],
                "ventesCount", r[4]
        )).toList();

        BigDecimal totalCA = agents.stream()
                .map(a -> (BigDecimal) a.get("totalCA"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalVentes = agents.stream()
                .mapToLong(a -> ((Number) a.get("ventesCount")).longValue())
                .sum();

        Map<String, Object> vars = Map.of(
                "agents",       agents,
                "societeNom",   societeNom,
                "from",         from,
                "to",           to,
                "totalCA",      totalCA,
                "totalVentes",  totalVentes,
                "generatedAt",  LocalDateTime.now()
        );
        return docGenService.renderToPdf("reports/agents-report", vars);
    }

    // ── Agents CSV ────────────────────────────────────────────────────────────

    public byte[] agentsCsv(UUID societeId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay()   : null;
        LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX) : null;
        List<Object[]> rows = venteRepo.agentsLeaderboardForReport(societeId, fromDt, toDt);
        StringBuilder sb = new StringBuilder();
        sb.append("Agent,CA total (MAD),Nombre de ventes\n");
        for (Object[] r : rows) {
            sb.append(escapeCsv(r[1] + " " + r[2]))
              .append(',').append(r[3])
              .append(',').append(r[4])
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Vente> fetchVentes(UUID societeId, LocalDate from, LocalDate to, VenteStatut statut) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay()      : null;
        LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX) : null;
        return venteRepo.findForReport(societeId, statut, fromDt, toDt);
    }

    private static String agentName(Vente v) {
        String p = v.getAgent().getPrenom();
        String n = v.getAgent().getNomFamille();
        if (p != null && n != null) return p + " " + n;
        if (p != null) return p;
        return n != null ? n : "";
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
