package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.legal.TvaCalculator;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportExportService {

    private final VenteRepository          venteRepo;
    private final SocieteRepository        societeRepo;
    private final DocumentGenerationService docGenService;
    private final PropertyRepository       propertyRepo;

    public ReportExportService(VenteRepository venteRepo,
                               SocieteRepository societeRepo,
                               DocumentGenerationService docGenService,
                               PropertyRepository propertyRepo) {
        this.venteRepo     = venteRepo;
        this.societeRepo   = societeRepo;
        this.docGenService = docGenService;
        this.propertyRepo  = propertyRepo;
    }

    // ── Ventes PDF ────────────────────────────────────────────────────────────

    public byte[] ventesPdf(UUID societeId, LocalDate from, LocalDate to, VenteStatut statut) {
        String societeNom = societeRepo.findById(societeId).map(s -> s.getNom()).orElse("");
        List<Vente> ventes = fetchVentes(societeId, from, to, statut);
        Map<UUID, BigDecimal> tauxByProperty = loadTauxTvaMap(ventes);

        BigDecimal totalHt  = BigDecimal.ZERO;
        BigDecimal totalTtc = BigDecimal.ZERO;
        Map<String, BigDecimal> subtotalByTaux = new java.util.LinkedHashMap<>();

        // Build pre-computed rows so the template stays free of BigDecimal arithmetic.
        List<Map<String, Object>> rows = new java.util.ArrayList<>(ventes.size());
        for (Vente v : ventes) {
            BigDecimal prixHt = v.getPrixVente();
            BigDecimal taux   = tauxByProperty.getOrDefault(v.getPropertyId(), TvaCalculator.TAUX_NORMAL);
            BigDecimal prixTtc = TvaCalculator.computePrixTtc(prixHt, taux);
            String tauxKey = taux.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
            if (prixHt != null) {
                totalHt  = totalHt.add(prixHt);
                totalTtc = totalTtc.add(prixTtc != null ? prixTtc : prixHt);
                subtotalByTaux.merge(tauxKey, prixHt, BigDecimal::add);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("vente",    v);
            row.put("prixHt",   prixHt);
            row.put("tauxPct",  tauxKey + "%");
            row.put("prixTtc",  prixTtc);
            row.put("agentNom", agentName(v));
            rows.add(row);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("rows",           rows);
        vars.put("societeNom",     societeNom);
        vars.put("from",           from);
        vars.put("to",             to);
        vars.put("statutFilter",   statut != null ? statut.name() : null);
        vars.put("total",          totalHt);
        vars.put("totalTtc",       totalTtc);
        vars.put("subtotalByTaux", subtotalByTaux);
        vars.put("generatedAt",    LocalDateTime.now());
        return docGenService.renderToPdf("reports/ventes-report", vars);
    }

    // ── Ventes CSV ────────────────────────────────────────────────────────────

    public byte[] ventesCsv(UUID societeId, LocalDate from, LocalDate to, VenteStatut statut) {
        List<Vente> ventes = fetchVentes(societeId, from, to, statut);
        Map<UUID, BigDecimal> tauxByProperty = loadTauxTvaMap(ventes);
        StringBuilder sb = new StringBuilder();
        sb.append("Référence,Acquéreur,Statut,Prix HT (MAD),Taux TVA,Prix TTC (MAD),Date compromis,Date livraison prévue,Agent\n");
        for (Vente v : ventes) {
            BigDecimal prixHt  = v.getPrixVente();
            BigDecimal taux    = tauxByProperty.getOrDefault(v.getPropertyId(), TvaCalculator.TAUX_NORMAL);
            BigDecimal prixTtc = TvaCalculator.computePrixTtc(prixHt, taux);
            String tauxPct     = prixHt != null
                    ? taux.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%"
                    : "";
            sb.append(escapeCsv(v.getVenteRef()))
              .append(',').append(escapeCsv(v.getContact().getFullName()))
              .append(',').append(v.getStatut().name())
              .append(',').append(prixHt  != null ? prixHt.toPlainString()  : "")
              .append(',').append(tauxPct)
              .append(',').append(prixTtc != null ? prixTtc.toPlainString() : "")
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

        Map<String, Object> vars = new HashMap<>();
        vars.put("agents",      agents);
        vars.put("societeNom",  societeNom);
        vars.put("from",        from);
        vars.put("to",          to);
        vars.put("totalCA",     totalCA);
        vars.put("totalVentes", totalVentes);
        vars.put("generatedAt", LocalDateTime.now());
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

    /**
     * Batch-loads tvaTaux for all properties referenced in the vente list.
     * Falls back to TAUX_NORMAL (20%) when a property has no commercial data.
     */
    private Map<UUID, BigDecimal> loadTauxTvaMap(List<Vente> ventes) {
        Set<UUID> ids = ventes.stream().map(Vente::getPropertyId).collect(Collectors.toSet());
        return propertyRepo.findAllById(ids).stream()
                .filter(p -> p.getTvaTaux() != null)
                .collect(Collectors.toMap(Property::getId, Property::getTvaTaux));
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
