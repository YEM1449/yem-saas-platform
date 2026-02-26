package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.contact.service.PropertyNotFoundException;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
    private TenantRepository tenantRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private PropertyService propertyService;

    private UUID tenantId;
    private Property mockProperty;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(UUID.randomUUID());

        Tenant mockTenant = new Tenant("test-key", "Test Tenant");
        Project mockProject = mock(Project.class);
        mockProperty = new Property(mockTenant, mockProject, PropertyType.VILLA, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listAll_noArgs_callsRepositoryWithNoFilters() {
        // Given
        when(propertyRepository.findByTenant_IdAndDeletedAtIsNull(tenantId))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll();

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findByTenant_IdAndDeletedAtIsNull(tenantId);
        verify(propertyRepository, never()).findByTenant_IdAndTypeAndDeletedAtIsNull(any(), any());
        verify(propertyRepository, never()).findByTenant_IdAndStatusAndDeletedAtIsNull(any(), any());
    }

    @Test
    void listAll_withNullParameters_callsRepositoryWithNoFilters() {
        // Given
        when(propertyRepository.findByTenant_IdAndDeletedAtIsNull(tenantId))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findByTenant_IdAndDeletedAtIsNull(tenantId);
    }

    @Test
    void listAll_withTypeOnly_callsRepositoryWithTypeFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        when(propertyRepository.findByTenant_IdAndTypeAndDeletedAtIsNull(tenantId, type))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findByTenant_IdAndTypeAndDeletedAtIsNull(tenantId, type);
        verify(propertyRepository, never()).findByTenant_IdAndDeletedAtIsNull(any());
    }

    @Test
    void listAll_withStatusOnly_callsRepositoryWithStatusFilter() {
        // Given
        PropertyStatus status = PropertyStatus.ACTIVE;
        when(propertyRepository.findByTenant_IdAndStatusAndDeletedAtIsNull(tenantId, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findByTenant_IdAndStatusAndDeletedAtIsNull(tenantId, status);
    }

    @Test
    void listAll_withBothFilters_callsRepositoryWithCombinedFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        PropertyStatus status = PropertyStatus.ACTIVE;
        when(propertyRepository.findByTenant_IdAndTypeAndStatusAndDeletedAtIsNull(tenantId, type, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findByTenant_IdAndTypeAndStatusAndDeletedAtIsNull(tenantId, type, status);
    }

    @Test
    void getById_nonExistentProperty_throwsNotFoundException() {
        // Given
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findByTenant_IdAndIdAndDeletedAtIsNull(tenantId, propertyId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> propertyService.getById(propertyId))
                .isInstanceOf(PropertyNotFoundException.class);
    }
}
