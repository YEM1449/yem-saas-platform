package com.yem.hlm.backend.property.domain;

import com.yem.hlm.backend.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Property entity for the CRM-HLM real estate system.
 * <p>
 * Supports 5 property types with type-specific validation:
 * VILLA, DUPLEX, APPARTEMENT, LOT, TERRAIN_VIERGE
 * <p>
 * Uses single-table approach with nullable type-specific fields.
 * Validation is enforced at the service layer based on the {@link PropertyType}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "property",
        indexes = {
                @Index(name = "idx_property_tenant_id", columnList = "tenant_id,id"),
                @Index(name = "idx_property_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "idx_property_tenant_type_status", columnList = "tenant_id,type,status"),
                @Index(name = "idx_property_tenant_city_region", columnList = "tenant_id,city,region"),
                @Index(name = "idx_property_tenant_price", columnList = "tenant_id,price"),
                @Index(name = "idx_property_tenant_created_at", columnList = "tenant_id,created_at DESC"),
                @Index(name = "idx_property_tenant_deleted_at", columnList = "tenant_id,deleted_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_property_tenant_reference", columnNames = {"tenant_id", "reference_code"})
        }
)
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_property_tenant"))
    private Tenant tenant;

    // ===== Core Classification Fields =====

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PropertyType type;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PropertyStatus status;

    @Setter
    @Column(name = "reference_code", nullable = false, length = 50)
    private String referenceCode;

    @Setter
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Setter
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Setter
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ===== Financial Fields =====

    @Setter
    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Setter
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Setter
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Setter
    @Column(name = "estimated_value", precision = 15, scale = 2)
    private BigDecimal estimatedValue;

    // ===== Location Fields =====

    @Setter
    @Column(name = "address", length = 500)
    private String address;

    @Setter
    @Column(name = "city", length = 100)
    private String city;

    @Setter
    @Column(name = "region", length = 100)
    private String region;

    @Setter
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Setter
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Setter
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    // ===== Legal Fields =====

    @Setter
    @Column(name = "title_deed_number", length = 100)
    private String titleDeedNumber;

    @Setter
    @Column(name = "cadastral_reference", length = 100)
    private String cadastralReference;

    @Setter
    @Column(name = "owner_name", length = 200)
    private String ownerName;

    @Setter
    @Column(name = "legal_status", length = 50)
    private String legalStatus;

    // ===== Physical Characteristics (Type-Specific, Nullable) =====

    /**
     * Surface area in square meters.
     * Required for: VILLA, DUPLEX, APPARTEMENT
     * Forbidden for: TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "surface_area_sqm", precision = 10, scale = 2)
    private BigDecimal surfaceAreaSqm;

    /**
     * Land area in square meters.
     * Required for: VILLA, LOT, TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "land_area_sqm", precision = 10, scale = 2)
    private BigDecimal landAreaSqm;

    /**
     * Number of bedrooms.
     * Required for: VILLA, DUPLEX, APPARTEMENT
     * Forbidden for: LOT, TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "bedrooms")
    private Integer bedrooms;

    /**
     * Number of bathrooms.
     * Required for: VILLA, DUPLEX, APPARTEMENT
     * Forbidden for: LOT, TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "bathrooms")
    private Integer bathrooms;

    /**
     * Number of floors.
     * Required for: DUPLEX
     * Optional for: VILLA
     */
    @Setter
    @Column(name = "floors")
    private Integer floors;

    /**
     * Number of parking spaces.
     * Optional for: VILLA, DUPLEX, APPARTEMENT
     */
    @Setter
    @Column(name = "parking_spaces")
    private Integer parkingSpaces;

    /**
     * Has garden (boolean).
     * Optional for: VILLA
     */
    @Setter
    @Column(name = "has_garden")
    private Boolean hasGarden;

    /**
     * Has swimming pool (boolean).
     * Optional for: VILLA
     */
    @Setter
    @Column(name = "has_pool")
    private Boolean hasPool;

    /**
     * Year of construction.
     * Optional for: VILLA, DUPLEX, APPARTEMENT
     * Forbidden for: LOT, TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "building_year")
    private Integer buildingYear;

    /**
     * Floor number in building.
     * Required for: APPARTEMENT
     */
    @Setter
    @Column(name = "floor_number")
    private Integer floorNumber;

    /**
     * Zoning classification (e.g., RESIDENTIAL, COMMERCIAL, MIXED).
     * Required for: LOT
     * Optional for: TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "zoning", length = 50)
    private String zoning;

    /**
     * Is serviced (utilities available).
     * Required for: LOT
     * Optional for: TERRAIN_VIERGE
     */
    @Setter
    @Column(name = "is_serviced")
    private Boolean isServiced;

    // ===== Lifecycle Fields =====

    @Setter
    @Column(name = "created_by")
    private UUID createdBy;

    @Setter
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Setter
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Setter
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Setter
    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    // ===== JPA Lifecycle Callbacks =====

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        // Set defaults
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "MAD";
        }
        if (this.status == null) {
            this.status = PropertyStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== Constructor =====

    /**
     * Creates a new property in DRAFT status.
     *
     * @param tenant        the tenant owning this property
     * @param type          the property type (VILLA, DUPLEX, etc.)
     * @param actorUserId   the user creating this property
     */
    public Property(Tenant tenant, PropertyType type, UUID actorUserId) {
        this.tenant = tenant;
        this.type = type;
        this.status = PropertyStatus.DRAFT;
        this.currency = "MAD"; // default currency
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    // ===== Business Methods =====

    /**
     * Marks the property as updated by the given user.
     */
    public void markUpdatedBy(UUID actorUserId) {
        this.updatedBy = actorUserId;
    }

    /**
     * Performs a soft delete by setting deletedAt timestamp.
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Checks if the property is soft-deleted.
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
