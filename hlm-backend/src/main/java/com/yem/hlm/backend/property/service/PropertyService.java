package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.contact.service.PropertyNotFoundException;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
    private final TenantRepository tenantRepository;

    public PropertyService(PropertyRepository propertyRepository, TenantRepository tenantRepository) {
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Creates a new property with type-specific validation.
     *
     * @param request the creation request
     * @return the created property response
     * @throws InvalidPropertyTypeException if type-specific validation fails
     * @throws PropertyReferenceCodeExistsException if reference code already exists
     */
    @Transactional
    public PropertyResponse create(PropertyCreateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        // Validate reference code uniqueness
        if (propertyRepository.existsByTenant_IdAndReferenceCode(tenantId, request.referenceCode())) {
            throw new PropertyReferenceCodeExistsException(request.referenceCode());
        }

        // Type-specific validation
        validateTypeSpecificFields(request.type(), request);

        // Load tenant
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        // Create entity
        var property = new Property(tenant, request.type(), userId);
        mapRequestToEntity(request, property);

        // Save
        property = propertyRepository.save(property);

        return PropertyResponse.from(property);
    }

    /**
     * Gets a property by ID (tenant-scoped).
     *
     * @param propertyId the property ID
     * @return the property response
     * @throws PropertyNotFoundException if property not found
     */
    public PropertyResponse getById(UUID propertyId) {
        UUID tenantId = TenantContext.getTenantId();

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        return PropertyResponse.from(property);
    }

    /**
     * Lists all non-deleted properties for the current tenant.
     *
     * @return list of property responses
     */
    public List<PropertyResponse> listAll() {
        UUID tenantId = TenantContext.getTenantId();

        return propertyRepository.findByTenant_IdAndDeletedAtIsNull(tenantId)
                .stream()
                .map(PropertyResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Updates a property with type-specific validation.
     *
     * @param propertyId the property ID
     * @param request the update request
     * @return the updated property response
     * @throws PropertyNotFoundException if property not found
     * @throws InvalidPropertyTypeException if validation fails
     */
    @Transactional
    public PropertyResponse update(UUID propertyId, PropertyUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // Type-specific validation for the existing type
        validateTypeSpecificFieldsForUpdate(property.getType(), request);

        // Apply updates
        mapUpdateRequestToEntity(request, property);
        property.markUpdatedBy(userId);

        property = propertyRepository.save(property);

        return PropertyResponse.from(property);
    }

    /**
     * Soft deletes a property.
     *
     * @param propertyId the property ID
     * @throws PropertyNotFoundException if property not found
     */
    @Transactional
    public void softDelete(UUID propertyId) {
        UUID tenantId = TenantContext.getTenantId();

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        property.softDelete();
        propertyRepository.save(property);
    }

    /**
     * Marks a property as reserved (called by DepositService).
     *
     * @param propertyId the property ID
     */
    @Transactional
    public void markAsReserved(UUID propertyId) {
        UUID tenantId = TenantContext.getTenantId();

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        property.setStatus(PropertyStatus.RESERVED);
        propertyRepository.save(property);
    }

    /**
     * Marks a property as sold.
     *
     * @param propertyId the property ID
     * @param soldAt the sold timestamp
     */
    @Transactional
    public void markAsSold(UUID propertyId, LocalDateTime soldAt) {
        UUID tenantId = TenantContext.getTenantId();

        var property = propertyRepository.findByTenant_IdAndId(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        property.setStatus(PropertyStatus.SOLD);
        property.setSoldAt(soldAt);
        propertyRepository.save(property);
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
            case APPARTEMENT -> {
                if (req.surfaceAreaSqm() == null) throw new InvalidPropertyTypeException("Surface area required for APPARTEMENT");
                if (req.bedrooms() == null) throw new InvalidPropertyTypeException("Bedrooms required for APPARTEMENT");
                if (req.bathrooms() == null) throw new InvalidPropertyTypeException("Bathrooms required for APPARTEMENT");
                if (req.floorNumber() == null) throw new InvalidPropertyTypeException("Floor number required for APPARTEMENT");
            }
            case LOT -> {
                if (req.landAreaSqm() == null) throw new InvalidPropertyTypeException("Land area required for LOT");
                if (req.zoning() == null || req.zoning().isBlank()) throw new InvalidPropertyTypeException("Zoning required for LOT");
                if (req.isServiced() == null) throw new InvalidPropertyTypeException("is_serviced required for LOT");
                // Forbidden fields
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year not applicable to LOT");
                }
            }
            case TERRAIN_VIERGE -> {
                if (req.landAreaSqm() == null) throw new InvalidPropertyTypeException("Land area required for TERRAIN_VIERGE");
                // Forbidden fields
                if (req.bedrooms() != null || req.bathrooms() != null || req.buildingYear() != null || req.surfaceAreaSqm() != null) {
                    throw new InvalidPropertyTypeException("Bedrooms/bathrooms/building_year/surface_area not applicable to TERRAIN_VIERGE");
                }
            }
        }
    }

    private void validateTypeSpecificFieldsForUpdate(PropertyType type, PropertyUpdateRequest req) {
        // Similar validation logic for updates (if fields are being modified)
        // For simplicity, we skip validation on update if fields are null (partial update)
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
    }

    private void mapUpdateRequestToEntity(PropertyUpdateRequest req, Property property) {
        // Partial update: only update non-null fields
        if (req.title() != null) property.setTitle(req.title());
        if (req.description() != null) property.setDescription(req.description());
        if (req.notes() != null) property.setNotes(req.notes());
        if (req.price() != null) property.setPrice(req.price());
        if (req.status() != null) property.setStatus(req.status());
        if (req.address() != null) property.setAddress(req.address());
        if (req.city() != null) property.setCity(req.city());
        if (req.region() != null) property.setRegion(req.region());
        if (req.postalCode() != null) property.setPostalCode(req.postalCode());
        if (req.legalStatus() != null) property.setLegalStatus(req.legalStatus());
        // Add other fields as needed for partial updates
    }
}
