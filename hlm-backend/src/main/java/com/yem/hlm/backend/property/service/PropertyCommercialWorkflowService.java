package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Single source of truth for Property commercial status transitions.
 * <p>
 * All code that needs to move a property through its commercial lifecycle
 * (ACTIVE → RESERVED → SOLD, or release back to ACTIVE) must go through
 * this service to keep timestamps consistent and avoid scattered inline saves.
 * <p>
 * Callers that already hold a property reference (e.g. {@code DepositService})
 * pass the loaded entity directly; callers that only have an ID use
 * {@link PropertyService#markAsReserved} / {@link PropertyService#markAsSold}.
 */
@Service
public class PropertyCommercialWorkflowService {

    private final PropertyRepository propertyRepository;

    public PropertyCommercialWorkflowService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Transitions a property to {@code RESERVED} and stamps {@code reservedAt}.
     *
     * @param property   the loaded, tenant-scoped property entity
     * @param reservedAt the timestamp to record (typically {@code LocalDateTime.now()})
     */
    @Transactional
    public void reserve(Property property, LocalDateTime reservedAt) {
        property.setStatus(PropertyStatus.RESERVED);
        property.setReservedAt(reservedAt);
        propertyRepository.save(property);
    }

    /**
     * Releases a reservation and returns the property to {@code ACTIVE}.
     * Clears {@code reservedAt}.
     *
     * @param property the loaded, tenant-scoped property entity
     */
    @Transactional
    public void releaseReservation(Property property) {
        property.setStatus(PropertyStatus.ACTIVE);
        property.setReservedAt(null);
        propertyRepository.save(property);
    }

    /**
     * Transitions a property to {@code SOLD} and stamps {@code soldAt}.
     * Intended for use by {@code SaleContractService} once implemented.
     *
     * @param property the loaded, tenant-scoped property entity
     * @param soldAt   the timestamp to record (typically contract sign date)
     */
    @Transactional
    public void sell(Property property, LocalDateTime soldAt) {
        property.setStatus(PropertyStatus.SOLD);
        property.setSoldAt(soldAt);
        propertyRepository.save(property);
    }
}
