package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.dashboard.api.dto.SalesIntelligenceDTO;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SalesIntelligenceService {

    private static final Map<String, String> TIME_LABELS = Map.of(
            "LT_30",   "< 30 jours",
            "D30_60",  "30 – 60 jours",
            "D61_90",  "61 – 90 jours",
            "D91_180", "91 – 180 jours",
            "GT_180",  "> 180 jours"
    );

    private static final Map<String, String> AGING_LABELS = Map.of(
            "FRESH",  "≤ 30 jours",
            "SHORT",  "31 – 90 jours",
            "MEDIUM", "91 – 180 jours",
            "LONG",   "181 – 365 jours",
            "STALE",  "> 1 an"
    );

    private static final List<String> TIME_BUCKET_ORDER =
            List.of("LT_30", "D30_60", "D61_90", "D91_180", "GT_180");

    private static final List<String> AGING_BUCKET_ORDER =
            List.of("FRESH", "SHORT", "MEDIUM", "LONG", "STALE");

    private final VenteRepository    venteRepo;
    private final PropertyRepository propertyRepo;

    public SalesIntelligenceService(VenteRepository venteRepo,
                                    PropertyRepository propertyRepo) {
        this.venteRepo    = venteRepo;
        this.propertyRepo = propertyRepo;
    }

    @Cacheable(
            value = CacheConfig.HOME_DASHBOARD_CACHE,
            key   = "'sales-intel:' + #societeId"
    )
    public SalesIntelligenceDTO getSnapshot(UUID societeId) {
        LocalDateTime now = LocalDateTime.now();

        // ── Inventory value ────────────────────────────────────────────────────
        List<Object[]> invRows = propertyRepo.inventoryValueSummary(societeId);
        Object[] invSummary = invRows.isEmpty() ? new Object[5] : invRows.get(0);
        BigDecimal unsoldValue        = toBD(invSummary[0]);
        long       activeCount        = toLong(invSummary[1]);
        long       reservedCount      = toLong(invSummary[2]);
        BigDecimal portfolioValue     = toBD(invSummary[3]);
        BigDecimal avgListPriceActive = toBD(invSummary[4]);

        // ── Sales breakdown by type ────────────────────────────────────────────
        List<Object[]> rawByType = venteRepo.salesBreakdownByType(societeId);
        List<SalesIntelligenceDTO.SalesByTypeRow> salesByType = rawByType.stream()
                .map(r -> new SalesIntelligenceDTO.SalesByTypeRow(
                        r[0] != null ? r[0].toString() : "INCONNU",
                        toLong(r[1]),
                        toBD(r[2]),
                        toBD(r[3]),
                        toDouble(r[4]),
                        toDouble(r[5])
                ))
                .toList();

        // ── Time-to-close distribution ──────────────────────────────────────────
        List<Object[]> rawTtc = venteRepo.timeToCloseBuckets(societeId);
        java.util.Map<String, Object[]> ttcMap = new java.util.HashMap<>();
        for (Object[] r : rawTtc) ttcMap.put(r[0].toString(), r);

        List<SalesIntelligenceDTO.TimeToCloseRow> ttcRows = new ArrayList<>();
        for (String bucket : TIME_BUCKET_ORDER) {
            Object[] r = ttcMap.get(bucket);
            ttcRows.add(new SalesIntelligenceDTO.TimeToCloseRow(
                    bucket,
                    TIME_LABELS.getOrDefault(bucket, bucket),
                    r != null ? toLong(r[1]) : 0L,
                    r != null ? toDouble(r[2]) : null
            ));
        }
        long ttcTotalCount = rawTtc.stream()
                .filter(r -> toDouble(r[2]) != null)
                .mapToLong(r -> toLong(r[1]))
                .sum();
        Double avgDaysToClose = ttcTotalCount == 0 ? null :
                rawTtc.stream()
                        .filter(r -> toDouble(r[2]) != null)
                        .mapToDouble(r -> toLong(r[1]) * toDouble(r[2]))
                        .sum() / ttcTotalCount;

        // ── Inventory aging ────────────────────────────────────────────────────
        List<Object[]> rawAging = propertyRepo.inventoryAgingBuckets(societeId);
        java.util.Map<String, Object[]> agingMap = new java.util.HashMap<>();
        for (Object[] r : rawAging) agingMap.put(r[0].toString(), r);

        List<SalesIntelligenceDTO.InventoryAgingRow> agingRows = new ArrayList<>();
        for (String bucket : AGING_BUCKET_ORDER) {
            Object[] r = agingMap.get(bucket);
            agingRows.add(new SalesIntelligenceDTO.InventoryAgingRow(
                    bucket,
                    AGING_LABELS.getOrDefault(bucket, bucket),
                    r != null ? toLong(r[1]) : 0L,
                    r != null ? toBD(r[2]) : BigDecimal.ZERO
            ));
        }

        // ── Price per sqm ──────────────────────────────────────────────────────
        List<Object[]> rawPriceType = propertyRepo.avgPricePerSqmByType(societeId);
        List<SalesIntelligenceDTO.PricePerSqmRow> priceByType = rawPriceType.stream()
                .map(r -> new SalesIntelligenceDTO.PricePerSqmRow(
                        r[0] != null ? r[0].toString() : "INCONNU",
                        toDouble(r[1]),
                        toBD(r[2]),
                        toBD(r[3]),
                        toLong(r[4])
                ))
                .toList();

        List<Object[]> rawPriceProj = venteRepo.avgPricePerSqmByProject(societeId);
        List<SalesIntelligenceDTO.PricePerSqmProjectRow> priceByProject = rawPriceProj.stream()
                .map(r -> new SalesIntelligenceDTO.PricePerSqmProjectRow(
                        r[0] != null ? r[0].toString() : null,
                        r[1] != null ? r[1].toString() : "—",
                        toDouble(r[2]),
                        toLong(r[3])
                ))
                .toList();

        long sqmTotalCount = priceByType.stream()
                .filter(r -> r.avgPricePerSqm() != null && r.avgPricePerSqm() > 0)
                .mapToLong(SalesIntelligenceDTO.PricePerSqmRow::count)
                .sum();
        Double globalAvgSqm = sqmTotalCount == 0 ? null :
                priceByType.stream()
                        .filter(r -> r.avgPricePerSqm() != null && r.avgPricePerSqm() > 0)
                        .mapToDouble(r -> r.count() * r.avgPricePerSqm())
                        .sum() / sqmTotalCount;

        return new SalesIntelligenceDTO(
                now,
                unsoldValue, portfolioValue, activeCount, reservedCount, avgListPriceActive,
                salesByType,
                avgDaysToClose, ttcRows,
                agingRows,
                globalAvgSqm, priceByType, priceByProject
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Long l)    return BigDecimal.valueOf(l);
        if (o instanceof Integer i) return BigDecimal.valueOf(i);
        if (o instanceof Number n) {
            double v = n.doubleValue();
            return Double.isFinite(v) ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
