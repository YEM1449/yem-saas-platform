package com.yem.hlm.backend.viewer3d.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.CrossSocieteAccessException;
import com.yem.hlm.backend.viewer3d.api.dto.*;
import com.yem.hlm.backend.viewer3d.domain.Lot3dMapping;
import com.yem.hlm.backend.viewer3d.domain.Project3dModel;
import com.yem.hlm.backend.viewer3d.repo.Lot3dMappingRepository;
import com.yem.hlm.backend.viewer3d.repo.Project3dModelRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates 3D model metadata storage and R2 pre-signed URL generation.
 */
@Service
@Transactional(readOnly = true)
public class Project3dService {

    static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final Project3dModelRepository modelRepository;
    private final Lot3dMappingRepository   mappingRepository;
    private final PropertyRepository       propertyRepository;
    private final ProjectRepository        projectRepository;
    private final MediaStorageService      storageService;

    public Project3dService(Project3dModelRepository modelRepository,
                            Lot3dMappingRepository mappingRepository,
                            PropertyRepository propertyRepository,
                            ProjectRepository projectRepository,
                            MediaStorageService storageService) {
        this.modelRepository    = modelRepository;
        this.mappingRepository  = mappingRepository;
        this.propertyRepository = propertyRepository;
        this.projectRepository  = projectRepository;
        this.storageService     = storageService;
    }

    // ── POST /api/projects/{projetId}/3d-model ───────────────────────────────

    @Transactional
    public Project3dModelResponse upsertModel(UUID projetId, Create3dModelRequest req) throws IOException {
        UUID societeId = requireSocieteId();
        UUID userId    = requireUserId();

        // Verify project belongs to this société
        projectRepository.findById(projetId)
                .filter(p -> societeId.equals(p.getSocieteId()))
                .orElseThrow(() -> new ProjectNotFoundException(projetId));

        Project3dModel model = modelRepository.findBySocieteIdAndProjetId(societeId, projetId)
                .orElseGet(() -> new Project3dModel(societeId, projetId, req.glbFileKey(),
                        req.dracoCompressed(), userId));

        model.setGlbFileKey(req.glbFileKey());
        model.setDracoCompressed(req.dracoCompressed());
        model.setUploadedAt(Instant.now());
        model.setUploadedByUserId(userId);

        model = modelRepository.save(model);

        String presignedUrl = storageService.generatePresignedUrl(model.getGlbFileKey(), PRESIGN_TTL);
        Instant expiresAt   = Instant.now().plus(PRESIGN_TTL);

        List<Lot3dMappingDto> mappings = buildMappingDtos(societeId, projetId);
        return new Project3dModelResponse(model.getId(), projetId, presignedUrl, expiresAt,
                model.isDracoCompressed(), mappings);
    }

    // ── GET /api/projects/{projetId}/3d-model ────────────────────────────────

    public Project3dModelResponse getModel(UUID projetId) throws IOException {
        UUID societeId = requireSocieteId();
        Project3dModel model = modelRepository.findBySocieteIdAndProjetId(societeId, projetId)
                .orElseThrow(() -> new Project3dModelNotFoundException(projetId));

        String presignedUrl = storageService.generatePresignedUrl(model.getGlbFileKey(), PRESIGN_TTL);
        Instant expiresAt   = Instant.now().plus(PRESIGN_TTL);
        List<Lot3dMappingDto> mappings = buildMappingDtos(societeId, projetId);

        return new Project3dModelResponse(model.getId(), projetId, presignedUrl, expiresAt,
                model.isDracoCompressed(), mappings);
    }

    // ── GET /api/projects/{projetId}/3d-properties-status (10 s cache) ───────

    @Cacheable(value = CacheConfig.LOT_STATUS_3D_CACHE,
               key = "'status:' + T(com.yem.hlm.backend.societe.SocieteContext).getSocieteId() + ':' + #projetId")
    public List<Lot3dStatusDto> getStatusSnapshot(UUID projetId) {
        UUID societeId = requireSocieteId();

        List<Lot3dMapping> mappings = mappingRepository.findBySocieteIdAndProjetId(societeId, projetId);
        if (mappings.isEmpty()) return List.of();

        Set<UUID> propertyIds = mappings.stream()
                .map(Lot3dMapping::getPropertyId)
                .collect(Collectors.toSet());

        // Fetch all properties in one query — société-scoped
        Map<UUID, Property> byId = propertyRepository
                .findAllBySocieteIdAndIdInAndDeletedAtIsNull(societeId, propertyIds)
                .stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));

        return mappings.stream()
                .filter(m -> byId.containsKey(m.getPropertyId()))
                .map(m -> {
                    Property p = byId.get(m.getPropertyId());
                    return new Lot3dStatusDto(
                            m.getMeshId(),
                            p.getId(),
                            toDisplayStatus(p.getStatus()),
                            p.getType() != null ? p.getType().name() : null,
                            p.getSurfaceAreaSqm(),
                            p.getPrice());
                })
                .toList();
    }

    // ── POST /api/projects/{projetId}/3d-model/upload-url ────────────────────

    static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    /**
     * Generates a pre-signed PUT URL so the client can upload the GLB directly to R2
     * without routing the file through the backend (two-step upload workflow).
     * The client must call {@link #upsertModel} with the returned {@code fileKey} after upload.
     */
    public UploadUrlResponse generateUploadUrl(UUID projetId, UploadUrlRequest req) throws IOException {
        UUID societeId = requireSocieteId();

        if (!req.dracoCompressed()) {
            throw new IllegalArgumentException(
                    "Le modèle 3D doit être compressé avec Draco avant l'upload.");
        }

        projectRepository.findById(projetId)
                .filter(p -> societeId.equals(p.getSocieteId()))
                .orElseThrow(() -> new ProjectNotFoundException(projetId));

        String fileKey = String.format("models/%s/%s/%s.glb", societeId, projetId, UUID.randomUUID());
        String uploadUrl = storageService.generatePresignedPutUrl(fileKey, UPLOAD_URL_TTL);

        return new UploadUrlResponse(uploadUrl, fileKey, Instant.now().plus(UPLOAD_URL_TTL));
    }

    // ── PUT /api/projects/{projetId}/3d-model/mappings ───────────────────────

    @Transactional
    @CacheEvict(value = CacheConfig.LOT_STATUS_3D_CACHE, allEntries = true)
    public void bulkUpsertMappings(UUID projetId, BulkMappingRequest req) {
        UUID societeId = requireSocieteId();

        // Verify project belongs to this société
        projectRepository.findById(projetId)
                .filter(p -> societeId.equals(p.getSocieteId()))
                .orElseThrow(() -> new ProjectNotFoundException(projetId));

        // Replace strategy: delete existing then re-insert
        mappingRepository.deleteAllBySocieteIdAndProjetId(societeId, projetId);

        List<Lot3dMapping> newMappings = req.mappings().stream()
                .map(e -> new Lot3dMapping(societeId, projetId, e.propertyId(),
                        e.meshId(), e.immeubleMeshId(), e.trancheMeshId()))
                .toList();

        mappingRepository.saveAll(newMappings);
    }

    // ── Portal helper ─────────────────────────────────────────────────────────

    /** Verify the portal user (contactId) has at least one vente in this project. */
    public boolean portalUserHasAccess(UUID projetId, UUID contactId) {
        UUID societeId = requireSocieteId();
        // Any property in this project mapped by the viewer that belongs to a vente of this contact
        List<Lot3dMapping> mappings = mappingRepository.findBySocieteIdAndProjetId(societeId, projetId);
        if (mappings.isEmpty()) return false;
        Set<UUID> propertyIds = mappings.stream().map(Lot3dMapping::getPropertyId).collect(Collectors.toSet());
        return propertyRepository.existsBySocieteIdAndContactVente(societeId, propertyIds, contactId);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private List<Lot3dMappingDto> buildMappingDtos(UUID societeId, UUID projetId) {
        List<Lot3dMapping> mappings = mappingRepository.findBySocieteIdAndProjetId(societeId, projetId);
        if (mappings.isEmpty()) return List.of();

        Set<UUID> propertyIds = mappings.stream()
                .map(Lot3dMapping::getPropertyId).collect(Collectors.toSet());
        Map<UUID, Property> byId = propertyRepository
                .findAllBySocieteIdAndIdInAndDeletedAtIsNull(societeId, propertyIds)
                .stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));

        return mappings.stream()
                .filter(m -> byId.containsKey(m.getPropertyId()))
                .map(m -> {
                    Property p = byId.get(m.getPropertyId());
                    return new Lot3dMappingDto(
                            m.getMeshId(),
                            m.getImmeubleMeshId(),
                            m.getTrancheMeshId(),
                            p.getId(),
                            p.getReferenceCode(),
                            p.getType() != null ? p.getType().name() : null,
                            p.getSurfaceAreaSqm(),
                            p.getPrice());
                })
                .toList();
    }

    private static String toDisplayStatus(PropertyStatus status) {
        if (status == null) return "DISPONIBLE";
        return switch (status) {
            case DRAFT, ACTIVE     -> "DISPONIBLE";
            case RESERVED          -> "RESERVE";
            case SOLD              -> "VENDU";
            case WITHDRAWN, ARCHIVED -> "LIVRE";
        };
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new CrossSocieteAccessException("Missing société context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = SocieteContext.getUserId();
        if (id == null) throw new CrossSocieteAccessException("Missing user context");
        return id;
    }
}
