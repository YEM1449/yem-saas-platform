package com.yem.hlm.backend.tranche.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yem.hlm.backend.immeuble.domain.Immeuble;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNameAlreadyExistsException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.api.dto.*;
import com.yem.hlm.backend.tranche.domain.Tranche;
import com.yem.hlm.backend.tranche.domain.TrancheStatut;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Bulk project generation service — the heart of the 5-step wizard.
 * <p>
 * A single call to {@link #generate} creates:
 * <ol>
 *   <li>One {@link Project}</li>
 *   <li>N {@link Tranche} records (one per tranche configured)</li>
 *   <li>M {@link Immeuble} records (buildings distributed across tranches)</li>
 *   <li>All property units per floor per building</li>
 *   <li>A {@code project_generation_log} audit entry</li>
 *   <li>A {@code project_generation_config} JSONB snapshot of the wizard config</li>
 * </ol>
 * All work is performed in a single transaction — either everything is created
 * or nothing is (rollback on any error).
 */
@Service
@Transactional
public class ProjectGenerationService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final ProjectRepository    projectRepo;
    private final TrancheRepository    trancheRepo;
    private final ImmeubleRepository   immeubleRepo;
    private final PropertyRepository   propertyRepo;
    private final JdbcTemplate         jdbc;
    private final CacheManager         cacheManager;

    public ProjectGenerationService(
            ProjectRepository projectRepo,
            TrancheRepository trancheRepo,
            ImmeubleRepository immeubleRepo,
            PropertyRepository propertyRepo,
            JdbcTemplate jdbc,
            CacheManager cacheManager) {
        this.projectRepo  = projectRepo;
        this.trancheRepo  = trancheRepo;
        this.immeubleRepo = immeubleRepo;
        this.propertyRepo = propertyRepo;
        this.jdbc         = jdbc;
        this.cacheManager = cacheManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public ProjectGenerationResponse generate(ProjectGenerationRequest req) {
        UUID societeId = requireSocieteId();
        UUID userId    = requireUserId();

        // 1. Project name uniqueness
        if (projectRepo.existsBySocieteIdAndName(societeId, req.projectNom())) {
            throw new ProjectNameAlreadyExistsException(req.projectNom());
        }

        // 2. Create project
        var project = new Project(societeId, req.projectNom());
        project.setDescription(req.projectDescription());
        project.setAdresse(req.projectAdresse());
        project.setVille(req.projectVille());
        project.setCodePostal(req.projectCodePostal());
        project.setMaitreOuvrage(req.maitreOuvrage());
        project.setDateOuvertureCommercialisation(req.dateOuvertureCommercialisation());
        project.setTvaTaux(req.tvaTaux());
        project.setSurfaceTerrainM2(req.surfaceTerrainM2());
        project.setPrixMoyenM2Cible(req.prixMoyenM2Cible());
        project = projectRepo.saveAndFlush(project);

        // 3. Generate tranches → buildings → units
        int globalBuildingOrder = 0;
        int globalUnitSeq       = 1;
        int totalBuildings       = 0;
        int totalUnits           = 0;
        BigDecimal totalValue    = BigDecimal.ZERO;
        Map<String, Integer> unitsByType = new LinkedHashMap<>();
        List<ProjectGenerationResponse.TrancheGenerationSummary> trancheSummaries = new ArrayList<>();

        for (TrancheConfig tCfg : req.tranches()) {

            Tranche tranche = new Tranche(societeId, project.getId(), tCfg.numero());
            tranche.setNom(tCfg.nom());
            tranche.setDateLivraisonPrevue(tCfg.dateLivraisonPrevue());
            tranche.setDateDebutTravaux(tCfg.dateDebutTravaux());
            tranche.setPermisConstruireRef(tCfg.permisConstruireRef());
            tranche.setStatut(TrancheStatut.EN_PREPARATION);
            tranche = trancheRepo.save(tranche);

            List<ProjectGenerationResponse.BuildingSummary> buildingSummaries = new ArrayList<>();
            int trancheUnits = 0;

            for (BuildingConfig bCfg : tCfg.buildings()) {
                String label     = resolveBuildingLabel(req.buildingNaming(), globalBuildingOrder, bCfg.customName());
                String buildingNom = req.buildingPrefix() + " " + label;

                Immeuble immeuble = new Immeuble(societeId, project, buildingNom);
                immeuble.setTrancheId(tranche.getId());
                immeuble.setNbEtages(bCfg.floorCount());
                immeuble = immeubleRepo.save(immeuble);

                int buildingUnitCount = 0;

                // Parking (sous-sol, floor = -1)
                if (bCfg.hasParking() && bCfg.parkingCount() > 0) {
                    for (int p = 1; p <= bCfg.parkingCount(); p++) {
                        String ref = (req.parkingPrefix() + label + String.format("%02d", p)).toUpperCase();
                        Property parking = buildUnit(societeId, project, PropertyType.PARKING,
                                ref, "Place " + ref, -1, null, null, null,
                                null, null, null, null, null,
                                tranche.getId(), immeuble, userId);
                        propertyRepo.save(parking);
                        unitsByType.merge("PARKING", 1, (a, b) -> a + b);
                        buildingUnitCount++;
                        globalUnitSeq++;
                    }
                }

                // Upper floors (or villa rows)
                for (FloorConfig fCfg : bCfg.floors()) {
                    PropertyType pType = resolvePropertyType(fCfg.propertyType());
                    for (int u = 1; u <= fCfg.unitCount(); u++) {
                        String ref = resolveUnitRef(req.unitRefPattern(), label,
                                fCfg.floorNumber(), u, globalUnitSeq,
                                req.unitPrefix(), req.rdcLabel());
                        globalUnitSeq++;
                        Property unit = buildUnit(societeId, project, pType,
                                ref, resolveTitle(pType, ref), fCfg.floorNumber(),
                                fCfg.surfaceMin(), fCfg.prixBase(), fCfg.orientation(),
                                fCfg.landAreaSqm(), fCfg.bedrooms(), fCfg.bathrooms(),
                                fCfg.hasPool(), fCfg.hasGarden(),
                                tranche.getId(), immeuble, userId);
                        propertyRepo.save(unit);
                        unitsByType.merge(fCfg.propertyType(), 1, (a, b) -> a + b);
                        if (fCfg.prixBase() != null) totalValue = totalValue.add(fCfg.prixBase());
                        buildingUnitCount++;
                    }
                }

                globalBuildingOrder++;
                totalUnits    += buildingUnitCount;
                trancheUnits  += buildingUnitCount;
                totalBuildings++;
                buildingSummaries.add(new ProjectGenerationResponse.BuildingSummary(
                        immeuble.getId(), buildingNom, bCfg.floorCount(), buildingUnitCount));
            }

            trancheSummaries.add(new ProjectGenerationResponse.TrancheGenerationSummary(
                    tranche.getId(), tranche.getDisplayNom(),
                    tranche.getDateLivraisonPrevue(),
                    buildingSummaries.size(), trancheUnits, buildingSummaries));
        }

        // 4. Persist generation config (JSONB snapshot)
        saveGenerationConfig(societeId, project.getId(), req);

        // 5. Persist audit log
        saveGenerationLog(societeId, project.getId(), userId,
                trancheSummaries.size(), totalBuildings, totalUnits);

        // 6. Evict project cache so list is fresh
        var cache = cacheManager.getCache("PROJECTS_CACHE");
        if (cache != null) cache.clear();

        String message = String.format(
                "%d tranche(s), %d bâtiment(s), %d lot(s) générés.",
                trancheSummaries.size(), totalBuildings, totalUnits);

        return new ProjectGenerationResponse(
                project.getId(), project.getName(),
                trancheSummaries.size(), totalBuildings, totalUnits,
                trancheSummaries, unitsByType, totalValue,
                "SUCCESS", message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Property buildUnit(UUID societeId, Project project, PropertyType type,
                                String ref, String title, int floorNumber,
                                BigDecimal surface, BigDecimal prix, String orientation,
                                BigDecimal landAreaSqm, Integer bedrooms, Integer bathrooms,
                                Boolean hasPool, Boolean hasGarden,
                                UUID trancheId, Immeuble immeuble, UUID userId) {
        var p = new Property(societeId, project, type, userId);
        p.setReferenceCode(ref.toUpperCase());
        p.setTitle(title);
        p.setFloorNumber(floorNumber >= 0 ? floorNumber : null);
        p.setSurfaceAreaSqm(surface);
        p.setPrice(prix != null ? prix : BigDecimal.ZERO);
        p.setOrientation(orientation);
        if (landAreaSqm != null) p.setLandAreaSqm(landAreaSqm);
        if (bedrooms    != null) p.setBedrooms(bedrooms);
        if (bathrooms   != null) p.setBathrooms(bathrooms);
        if (hasPool   != null)   p.setHasPool(hasPool);
        if (hasGarden != null)   p.setHasGarden(hasGarden);
        p.setTrancheId(trancheId);
        p.setImmeuble(immeuble);
        return p;
    }

    private String resolveTitle(PropertyType type, String ref) {
        return switch (type) {
            case APPARTEMENT -> "Appartement " + ref;
            case STUDIO      -> "Studio " + ref;
            case COMMERCE    -> "Local commercial " + ref;
            case DUPLEX      -> "Duplex " + ref;
            case T2          -> "T2 " + ref;
            case T3          -> "T3 " + ref;
            case PARKING     -> "Place de parking " + ref;
            case VILLA       -> "Villa " + ref;
            default          -> ref;
        };
    }

    private PropertyType resolvePropertyType(String typeStr) {
        try {
            return PropertyType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return PropertyType.APPARTEMENT;
        }
    }

    private String resolveBuildingLabel(String naming, int order, String custom) {
        return switch (naming) {
            case "LETTRE"  -> String.valueOf((char) ('A' + order));
            case "CHIFFRE" -> String.valueOf(order + 1);
            case "CUSTOM"  -> (custom != null && !custom.isBlank()) ? custom
                              : String.valueOf((char) ('A' + order));
            default        -> String.valueOf((char) ('A' + order));
        };
    }

    private String resolveUnitRef(String pattern, String building,
                                   int floor, int unitNum, int seq,
                                   String prefix, String rdcLabel) {
        return switch (pattern) {
            case "BUILDING_FLOOR_UNIT" ->
                    building + String.format("%d%02d", Math.max(floor, 0), unitNum);
            case "FLOOR_UNIT" ->
                    (floor == 0 ? (rdcLabel != null ? rdcLabel : "RDC") : String.valueOf(floor))
                    + String.format("%02d", unitNum);
            case "SEQUENTIAL" ->
                    (prefix != null && !prefix.isBlank() ? prefix : "") + String.format("%03d", seq);
            default ->
                    building + String.format("%d%02d", Math.max(floor, 0), unitNum);
        };
    }

    private void saveGenerationConfig(UUID societeId, UUID projectId, ProjectGenerationRequest req) {
        try {
            String json = MAPPER.writeValueAsString(req);
            jdbc.update("""
                    INSERT INTO project_generation_config (societe_id, project_id, config_json)
                    VALUES (?::uuid, ?::uuid, ?::jsonb)
                    ON CONFLICT (project_id) DO UPDATE SET config_json = EXCLUDED.config_json
                    """, societeId.toString(), projectId.toString(), json);
        } catch (JsonProcessingException e) {
            // Non-fatal — log only
        }
    }

    private void saveGenerationLog(UUID societeId, UUID projectId, UUID userId,
                                    int tranches, int buildings, int units) {
        jdbc.update("""
                INSERT INTO project_generation_log
                  (societe_id, project_id, generated_by, tranches_count, buildings_count, units_count, status)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, 'SUCCESS')
                """, societeId.toString(), projectId.toString(), userId.toString(),
                tranches, buildings, units);
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new IllegalStateException("Missing société context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = SocieteContext.getUserId();
        if (id == null) throw new IllegalStateException("Missing user context");
        return id;
    }
}
