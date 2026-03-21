package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PropertyCommercialWorkflowService.
 * Verifies status transitions and timestamp stamping on the entity before save.
 */
@ExtendWith(MockitoExtension.class)
class PropertyCommercialWorkflowServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyCommercialWorkflowService service;

    private Property property;

    @BeforeEach
    void setUp() {
        UUID societeId = UUID.randomUUID();
        Project project = mock(Project.class);
        property = new Property(societeId, project, PropertyType.VILLA, UUID.randomUUID());
        // Start in ACTIVE status for transition tests
        property.setStatus(PropertyStatus.ACTIVE);

        when(propertyRepository.save(property)).thenReturn(property);
    }

    // ===== reserve =====

    @Test
    void reserve_setsStatusReserved_andStampsReservedAt() {
        LocalDateTime reservedAt = LocalDateTime.now();

        service.reserve(property, reservedAt);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.RESERVED);
        assertThat(property.getReservedAt()).isEqualTo(reservedAt);
        verify(propertyRepository).save(property);
    }

    @Test
    void reserve_savesProperty() {
        service.reserve(property, LocalDateTime.now());

        verify(propertyRepository, times(1)).save(property);
    }

    // ===== releaseReservation =====

    @Test
    void releaseReservation_setsStatusActive_andClearsReservedAt() {
        // Arrange: property is currently RESERVED
        property.setStatus(PropertyStatus.RESERVED);
        property.setReservedAt(LocalDateTime.now().minusDays(1));

        service.releaseReservation(property);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.ACTIVE);
        assertThat(property.getReservedAt()).isNull();
        verify(propertyRepository).save(property);
    }

    @Test
    void releaseReservation_savesProperty() {
        property.setStatus(PropertyStatus.RESERVED);

        service.releaseReservation(property);

        verify(propertyRepository, times(1)).save(property);
    }

    // ===== sell =====

    @Test
    void sell_setsStatusSold_andStampsSoldAt() {
        property.setStatus(PropertyStatus.RESERVED);
        LocalDateTime soldAt = LocalDateTime.now();

        service.sell(property, soldAt);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.SOLD);
        assertThat(property.getSoldAt()).isEqualTo(soldAt);
        verify(propertyRepository).save(property);
    }

    @Test
    void sell_savesProperty() {
        service.sell(property, LocalDateTime.now());

        verify(propertyRepository, times(1)).save(property);
    }

    // ===== transition integrity =====

    @Test
    void reserveThenRelease_leavesReservedAtNull() {
        LocalDateTime reservedAt = LocalDateTime.now();
        service.reserve(property, reservedAt);

        // Re-stub for second save call
        when(propertyRepository.save(property)).thenReturn(property);
        service.releaseReservation(property);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.ACTIVE);
        assertThat(property.getReservedAt()).isNull();
    }
}
