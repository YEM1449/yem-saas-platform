package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.service.ProjectActiveGuard;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PropertyService focusing on API contract and null-safety.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private SocieteRepository societeRepository;

    @Mock
    private ProjectActiveGuard projectActiveGuard;

    @InjectMocks
    private PropertyService propertyService;

    private UUID societeId;
    private Property mockProperty;

    @BeforeEach
    void setUp() {
        societeId = UUID.randomUUID();
        SocieteContext.setSocieteId(societeId);
        SocieteContext.setUserId(UUID.randomUUID());

        Project mockProject = mock(Project.class);
        mockProperty = new Property(societeId, mockProject, PropertyType.VILLA, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    @Test
    void listAll_noArgs_callsRepositoryWithNoFilters() {
        // Given
        when(propertyRepository.findBySocieteIdAndDeletedAtIsNull(societeId))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll();

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findBySocieteIdAndDeletedAtIsNull(societeId);
        verify(propertyRepository, never()).findBySocieteIdAndTypeAndDeletedAtIsNull(any(), any());
        verify(propertyRepository, never()).findBySocieteIdAndStatusAndDeletedAtIsNull(any(), any());
    }

    @Test
    void listAll_withNullParameters_callsRepositoryWithNoFilters() {
        // Given
        when(propertyRepository.findBySocieteIdAndDeletedAtIsNull(societeId))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findBySocieteIdAndDeletedAtIsNull(societeId);
    }

    @Test
    void listAll_withTypeOnly_callsRepositoryWithTypeFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        when(propertyRepository.findBySocieteIdAndTypeAndDeletedAtIsNull(societeId, type))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findBySocieteIdAndTypeAndDeletedAtIsNull(societeId, type);
        verify(propertyRepository, never()).findBySocieteIdAndDeletedAtIsNull(any());
    }

    @Test
    void listAll_withStatusOnly_callsRepositoryWithStatusFilter() {
        // Given
        PropertyStatus status = PropertyStatus.ACTIVE;
        when(propertyRepository.findBySocieteIdAndStatusAndDeletedAtIsNull(societeId, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findBySocieteIdAndStatusAndDeletedAtIsNull(societeId, status);
    }

    @Test
    void listAll_withBothFilters_callsRepositoryWithCombinedFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        PropertyStatus status = PropertyStatus.ACTIVE;
        when(propertyRepository.findBySocieteIdAndTypeAndStatusAndDeletedAtIsNull(societeId, type, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findBySocieteIdAndTypeAndStatusAndDeletedAtIsNull(societeId, type, status);
    }

    @Test
    void getById_nonExistentProperty_throwsNotFoundException() {
        // Given
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> propertyService.getById(propertyId))
                .isInstanceOf(PropertyNotFoundException.class);
    }
}
