package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.immeuble.domain.Immeuble;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.immeuble.service.ImmeubleNotFoundException;
import com.yem.hlm.backend.project.service.ProjectActiveGuard;
import com.yem.hlm.backend.property.api.dto.BulkStatusRequest;
import com.yem.hlm.backend.property.api.dto.BulkStatusResult;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyStatusUpdateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.CrossSocieteAccessException;
import com.yem.hlm.backend.societe.QuotaService;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Property entity operations with type-specific validation.
 * <p>
 * Enforces validation rules based on PropertyType:
 * - VILLA: requires surface_area, land_area, bedrooms, bathrooms
 * - DUPLEX: requires surface_area, bedrooms, bathrooms, floors
 * - APPARTEMENT: requires surface_area, bedrooms, bathrooms, floor_number
 * - LOT: requires land_area, zoning, is_serviced; forbids bedrooms, bathrooms, building_year
 * - TERRAIN_VIERGE: requires land_area; forbids bedrooms, bathrooms, building_year, surface_area
 */
@Service
@Transactional(readOnly = true)
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final ProjectActiveGuard projectActiveGuard;
    private final PropertyCommercialWorkflowService propertyCommercialWorkflowService;
    private final ImmeubleRepository immeubleRepository;
    private final QuotaService quotaService;

    public PropertyService(PropertyRepository propertyRepository,
                           ProjectActiveGuard projectActiveGuard,
                           PropertyCommercialWorkflowService propertyCommercialWorkflowService,
                           ImmeubleRepository immeubleRepository,
                           QuotaService quotaService) {
        this.propertyRepository = propertyRepository;
        this.projectActiveGuard = projectActiveGuard;
        this.propertyCommercialWorkflowService = propertyCommercialWorkflowService;
        this.immeubleRepository = immeubleRepository;
        this.quotaService = quotaService;
    }

    @Transactional
    public PropertyResponse create(PropertyCreateRequest request) {
        UUID societeId = requireSocieteId();
        UUID userId = requireUserId();
        quotaService.enforceBienQuota(societeId);

        // Validate reference code uniqueness
        if (propertyRepository.existsBySocieteIdAndReferenceCode(societeId, request.referenceCode())) {
            throw new PropertyReferenceCodeExistsException(request.referenceCode());
        }

        // Type-specific validation
        validateTypeSpecificFields(request.type(), request);

        // Load project and assert it belongs to société and is ACTIVE
        var project = projectActiveGuard.requireActive(societeId, request.projectId());

        // Create entity
        var property = new Property(societeId, project, request.type(), userId);
        mapRequestToEntity(request, property);

        // Save
        property = propertyRepository.save(property);

        return PropertyResponse.from(property);
    }

    public PropertyResponse getById(UUID propertyId) {
        UUID societeId = requireSocieteId();

        var property = propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        return PropertyResponse.from(property);
    }

    public List<PropertyResponse> listAll() {
        return listAll(null, null, null, null);
    }

    public List<PropertyResponse> listAll(PropertyType type, PropertyStatus status) {
        return listAll(null, null, type, status);
    }

    public List<PropertyResponse> listAll(UUID projectId, UUID immeubleId,
                                           PropertyType type, PropertyStatus status) {
        UUID societeId = requireSocieteId();

        List<Property> properties = propertyRepository.findWithFilters(
                societeId, projectId, immeubleId, type, status);

        return properties.stream()
                .map(PropertyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public PropertyResponse update(UUID propertyId, PropertyUpdateRequest request) {
        UUID societeId = requireSocieteId();
        UUID userId = requireUserId();

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // Type-specific validation for the existing type
        validateTypeSpecificFieldsForUpdate(property.getType(), request);

        // Apply updates
        mapUpdateRequestToEntity(request, property);
        property.markUpdatedBy(userId);

        property = propertyRepository.save(property);

        return PropertyResponse.from(property);
    }

    @Transactional
    public void softDelete(UUID propertyId) {
        UUID societeId = requireSocieteId();

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        property.softDelete();
        propertyRepository.save(property);
    }

    /**
     * Changes the editorial status of a property.
     * <p>
     * Only non-commercial statuses may be set here: DRAFT, ACTIVE, WITHDRAWN, ARCHIVED.
     * RESERVED and SOLD are exclusively managed by the commercial workflow
     * (reservation/deposit/contract services) and are rejected with
     * {@link InvalidPropertyStatusTransitionException}.
     */
    @Transactional
    public PropertyResponse updateEditorialStatus(UUID propertyId, PropertyStatusUpdateRequest req) {
        PropertyStatus requested = req.status();
        if (requested == PropertyStatus.RESERVED || requested == PropertyStatus.SOLD) {
            throw new InvalidPropertyStatusTransitionException(
                    "Status " + requested + " can only be set by the commercial workflow " +
                    "(reservation / deposit / contract). Use the reservation or contract endpoints.");
        }

        UUID societeId = requireSocieteId();
        UUID userId = requireUserId();

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // Guard: RESERVED and SOLD properties are under commercial lock —
        // do not allow editorial re-classification while a commercial event is active.
        if (property.getStatus() == PropertyStatus.RESERVED
                || property.getStatus() == PropertyStatus.SOLD) {
            throw new InvalidPropertyStatusTransitionException(
                    "Property is currently " + property.getStatus()
                    + " — cancel the reservation or contract before changing the editorial status.");
        }

        property.setStatus(requested);
        property.markUpdatedBy(userId);
        property = propertyRepository.save(property);
        return PropertyResponse.from(property);
    }

    /**
     * Applies a single editorial status to multiple properties in one transaction.
     * Properties that are RESERVED or SOLD, or not found in the société, are silently skipped
     * (returned in {@code skipped}) rather than failing the whole batch.
     * RESERVED and SOLD may not be set here — use the commercial workflow endpoints.
     */
    @Transactional
    public BulkStatusResult bulkUpdateEditorialStatus(BulkStatusRequest req) {
        if (req.status() == PropertyStatus.RESERVED || req.status() == PropertyStatus.SOLD) {
            throw new InvalidPropertyStatusTransitionException(
                    "Status " + req.status() + " cannot be set via the bulk endpoint — " +
                    "use the commercial workflow (reservation/contract).");
        }

        UUID societeId = requireSocieteId();
        UUID userId    = requireUserId();
        int updated = 0, skipped = 0;

        for (UUID id : req.ids()) {
            var opt = propertyRepository.findBySocieteIdAndId(societeId, id);
            if (opt.isEmpty()) { skipped++; continue; }

            var property = opt.get();
            if (property.getStatus() == PropertyStatus.RESERVED
                    || property.getStatus() == PropertyStatus.SOLD) {
                skipped++;
                continue;
            }

            property.setStatus(req.status());
            property.markUpdatedBy(userId);
            propertyRepository.save(property);
            updated++;
        }

        return new BulkStatusResult(updated, skipped);
    }

    @Transactional
    public void markAsReserved(UUID propertyId) {
        UUID societeId = requireSocieteId();

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        propertyCommercialWorkflowService.reserve(property, LocalDateTime.now());
    }

    @Transactional
    public void markAsSold(UUID propertyId, LocalDateTime soldAt) {
        UUID societeId = requireSocieteId();

        var property = propertyRepository.findBySocieteIdAndId(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        propertyCommercialWorkflowService.sell(property, soldAt);
    }

    // ===== Type-Specific Validation Logic =====

    private void validateTypeSpecificFields(PropertyType type, PropertyCreateRequest req) {
        switch (type) {
            case VILLA -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for VILLA");
                if (req.landAreaSqm() == null) throw new InvalidPropertyTypeException("Land area required for VILLA");
                if (req.bedrooms() == null) throw new InvalidPropertyTypeException("Bedrooms required for VILLA");
                if (req.bathrooms() == null) throw new InvalidPropertyTypeException("Bathrooms required for VILLA");
            }
            case DUPLEX -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for DUPLEX");
                if (req.bedrooms() == null) throw new InvalidPropertyTypeException("Bedrooms required for DUPLEX");
                if (req.bathrooms() == null) throw new InvalidPropertyTypeException("Bathrooms required for DUPLEX");
                if (req.floors() == null) throw new InvalidPropertyTypeException("Floors required for DUPLEX");
            }
            case APPARTEMENT, T2, T3 -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for " + type);
                if (req.bedrooms() == null) throw new InvalidPropertyTypeException("Bedrooms required for " + type);
                if (req.bathrooms() == null) throw new InvalidPropertyTypeException("Bathrooms required for " + type);
                if (req.floorNumber() == null) throw new InvalidPropertyTypeException("Floor number required for " + type);
            }
            case STUDIO -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for STUDIO");
                if (req.floorNumber() == null) throw new InvalidPropertyTypeException("Floor number required for STUDIO");
            }
            case COMMERCE -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for COMMERCE");
            }
            case LOT -> {
                if (req.landAreaSqm() == null) throw new InvalidPropertyTypeException("Land area required for LOT");
                if (req.zoning() == null || req.zoning().isBlank()) throw new InvalidPropertyTypeException("Zoning required for LOT");
                if (req.isServiced() == null) throw new InvalidPropertyTypeException("is_serviced required for LOT");
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year not applicable to LOT");
                }
            }
            case TERRAIN_VIERGE -> {
                if (req.landAreaSqm() == null) throw new InvalidPropertyTypeException("Land area required for TERRAIN_VIERGE");
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null || req.surfaceAreaSqm() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year/surface_area not applicable to TERRAIN_VIERGE");
                }
            }
            case PARKING -> { /* no type-specific required fields — reference code and price are sufficient */ }
        }
    }

    private void validateTypeSpecificFieldsForUpdate(PropertyType type, PropertyUpdateRequest req) {
        switch (type) {
            case LOT -> {
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year not applicable to LOT");
                }
            }
            case TERRAIN_VIERGE -> {
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null || req.surfaceAreaSqm() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year/surface_area not applicable to TERRAIN_VIERGE");
                }
            }
            default -> { /* PARKING and other types: partial updates on physical fields are allowed */ }
        }
    }

    private void mapRequestToEntity(PropertyCreateRequest req, Property property) {
        property.setReferenceCode(req.referenceCode());
        property.setTitle(req.title());
        property.setDescription(req.description());
        property.setNotes(req.notes());
        property.setPrice(req.price());
        property.setCurrency(req.currency());
        property.setCommissionRate(req.commissionRate());
        property.setEstimatedValue(req.estimatedValue());
        property.setAddress(req.address());
        property.setCity(req.city());
        property.setRegion(req.region());
        property.setPostalCode(req.postalCode());
        property.setLatitude(req.latitude());
        property.setLongitude(req.longitude());
        property.setTitleDeedNumber(req.titleDeedNumber());
        property.setCadastralReference(req.cadastralReference());
        property.setOwnerName(req.ownerName());
        property.setLegalStatus(req.legalStatus());
        property.setSurfaceAreaSqm(req.surfaceAreaSqm());
        property.setLandAreaSqm(req.landAreaSqm());
        property.setBedrooms(req.bedrooms());
        property.setBathrooms(req.bathrooms());
        property.setFloors(req.floors());
        property.setParkingSpaces(req.parkingSpaces());
        property.setHasGarden(req.hasGarden());
        property.setHasPool(req.hasPool());
        property.setBuildingYear(req.buildingYear());
        property.setFloorNumber(req.floorNumber());
        property.setZoning(req.zoning());
        property.setIsServiced(req.isServiced());
        property.setListedForSale(req.listedForSale() != null && req.listedForSale());
        property.setBuildingName(req.buildingName());
        if (req.immeubleId() != null) {
            property.setImmeuble(loadImmeubleForProject(req.immeubleId(), property.getProject().getId()));
        }
    }

    private void mapUpdateRequestToEntity(PropertyUpdateRequest req, Property property) {
        if (req.title() != null) property.setTitle(req.title());
        if (req.description() != null) property.setDescription(req.description());
        if (req.notes() != null) property.setNotes(req.notes());
        if (req.price() != null) property.setPrice(req.price());
        if (req.address() != null) property.setAddress(req.address());
        if (req.city() != null) property.setCity(req.city());
        if (req.region() != null) property.setRegion(req.region());
        if (req.postalCode() != null) property.setPostalCode(req.postalCode());
        if (req.legalStatus() != null) property.setLegalStatus(req.legalStatus());
        if (req.surfaceAreaSqm() != null) property.setSurfaceAreaSqm(req.surfaceAreaSqm());
        if (req.landAreaSqm() != null) property.setLandAreaSqm(req.landAreaSqm());
        if (req.bedrooms() != null) property.setBedrooms(req.bedrooms());
        if (req.bathrooms() != null) property.setBathrooms(req.bathrooms());
        if (req.floors() != null) property.setFloors(req.floors());
        if (req.parkingSpaces() != null) property.setParkingSpaces(req.parkingSpaces());
        if (req.hasGarden() != null) property.setHasGarden(req.hasGarden());
        if (req.hasPool() != null) property.setHasPool(req.hasPool());
        if (req.buildingYear() != null) property.setBuildingYear(req.buildingYear());
        if (req.floorNumber() != null) property.setFloorNumber(req.floorNumber());
        if (req.zoning() != null) property.setZoning(req.zoning());
        if (req.isServiced() != null) property.setIsServiced(req.isServiced());
        if (req.listedForSale() != null) property.setListedForSale(req.listedForSale());
        UUID previousProjectId = property.getProject().getId();
        boolean projectChanged = false;
        if (req.projectId() != null) {
            UUID societeId = requireSocieteId();
            var project = projectActiveGuard.requireActive(societeId, req.projectId());
            property.setProject(project);
            projectChanged = !project.getId().equals(previousProjectId);
        }
        if (req.buildingName() != null) property.setBuildingName(req.buildingName());
        if (req.immeubleId() != null) {
            property.setImmeuble(loadImmeubleForProject(req.immeubleId(), property.getProject().getId()));
        } else if (projectChanged) {
            property.setImmeuble(null);
        }
    }

    private Immeuble loadImmeubleForProject(UUID immeubleId, UUID projectId) {
        UUID societeId = requireSocieteId();
        Immeuble immeuble = immeubleRepository.findBySocieteIdAndId(societeId, immeubleId)
                .orElseThrow(() -> new ImmeubleNotFoundException(immeubleId));

        if (!immeuble.getProject().getId().equals(projectId)) {
            throw new ImmeubleProjectMismatchException(immeubleId, projectId);
        }

        return immeuble;
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
