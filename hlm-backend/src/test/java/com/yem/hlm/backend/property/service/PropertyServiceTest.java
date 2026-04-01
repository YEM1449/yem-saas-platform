package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.immeuble.domain.Immeuble;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.immeuble.service.ImmeubleNotFoundException;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.service.ProjectActiveGuard;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
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
    private ProjectActiveGuard projectActiveGuard;

    @Mock
    private PropertyCommercialWorkflowService propertyCommercialWorkflowService;

    @Mock
    private ImmeubleRepository immeubleRepository;

    @InjectMocks
    private PropertyService propertyService;

    private UUID societeId;
    private Property mockProperty;
    private UUID userId;
    private Project mockProject;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        societeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        SocieteContext.setSocieteId(societeId);
        SocieteContext.setUserId(userId);

        mockProject = mock(Project.class);
        mockProperty = new Property(societeId, mockProject, PropertyType.VILLA, userId);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    @Test
    void listAll_noArgs_callsRepositoryWithNullFilters() {
        // Given
        stubMockProject(projectId, "Project A");
        when(propertyRepository.findWithFilters(societeId, null, null, null, null))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll();

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findWithFilters(societeId, null, null, null, null);
    }

    @Test
    void listAll_withNullParameters_callsRepositoryWithNullFilters() {
        // Given
        stubMockProject(projectId, "Project A");
        when(propertyRepository.findWithFilters(societeId, null, null, null, null))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findWithFilters(societeId, null, null, null, null);
    }

    @Test
    void listAll_withTypeOnly_callsRepositoryWithTypeFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        stubMockProject(projectId, "Project A");
        when(propertyRepository.findWithFilters(societeId, null, null, type, null))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findWithFilters(societeId, null, null, type, null);
    }

    @Test
    void listAll_withStatusOnly_callsRepositoryWithStatusFilter() {
        // Given
        PropertyStatus status = PropertyStatus.ACTIVE;
        stubMockProject(projectId, "Project A");
        when(propertyRepository.findWithFilters(societeId, null, null, null, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(null, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findWithFilters(societeId, null, null, null, status);
    }

    @Test
    void listAll_withBothFilters_callsRepositoryWithCombinedFilter() {
        // Given
        PropertyType type = PropertyType.VILLA;
        PropertyStatus status = PropertyStatus.ACTIVE;
        stubMockProject(projectId, "Project A");
        when(propertyRepository.findWithFilters(societeId, null, null, type, status))
                .thenReturn(List.of(mockProperty));

        // When
        List<PropertyResponse> result = propertyService.listAll(type, status);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyRepository).findWithFilters(societeId, null, null, type, status);
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

    @Test
    void create_withUnknownImmeuble_throwsImmeubleNotFoundException() {
        UUID immeubleId = UUID.randomUUID();
        PropertyCreateRequest request = validCreateRequest(projectId, immeubleId);

        when(mockProject.getId()).thenReturn(projectId);
        when(propertyRepository.existsBySocieteIdAndReferenceCode(societeId, request.referenceCode()))
                .thenReturn(false);
        when(projectActiveGuard.requireActive(societeId, projectId)).thenReturn(mockProject);
        when(immeubleRepository.findBySocieteIdAndId(societeId, immeubleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.create(request))
                .isInstanceOf(ImmeubleNotFoundException.class)
                .hasMessage("Immeuble not found: " + immeubleId);
    }

    @Test
    void update_whenProjectChangesWithoutImmeuble_clearsExistingImmeuble() {
        UUID propertyId = UUID.randomUUID();
        UUID newProjectId = UUID.randomUUID();

        Project newProject = mock(Project.class);
        when(newProject.getId()).thenReturn(newProjectId);
        when(newProject.getName()).thenReturn("Project B");
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Immeuble existingImmeuble = mock(Immeuble.class);
        mockProperty.setImmeuble(existingImmeuble);

        when(propertyRepository.findBySocieteIdAndId(societeId, propertyId)).thenReturn(Optional.of(mockProperty));
        when(projectActiveGuard.requireActive(societeId, newProjectId)).thenReturn(newProject);

        PropertyResponse response = propertyService.update(propertyId, new PropertyUpdateRequest(
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, newProjectId, null, null
        ));

        assertThat(mockProperty.getProject()).isSameAs(newProject);
        assertThat(mockProperty.getImmeuble()).isNull();
        assertThat(response.projectId()).isEqualTo(newProjectId);
        assertThat(response.immeubleId()).isNull();
    }

    @Test
    void update_withImmeubleFromDifferentProject_throwsMismatchException() {
        UUID propertyId = UUID.randomUUID();
        UUID immeubleId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();

        Project otherProject = mock(Project.class);
        when(otherProject.getId()).thenReturn(otherProjectId);

        Immeuble immeuble = mock(Immeuble.class);
        when(immeuble.getProject()).thenReturn(otherProject);
        when(mockProject.getId()).thenReturn(projectId);

        when(propertyRepository.findBySocieteIdAndId(societeId, propertyId)).thenReturn(Optional.of(mockProperty));
        when(immeubleRepository.findBySocieteIdAndId(societeId, immeubleId)).thenReturn(Optional.of(immeuble));

        assertThatThrownBy(() -> propertyService.update(propertyId, new PropertyUpdateRequest(
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, immeubleId, null
        )))
                .isInstanceOf(ImmeubleProjectMismatchException.class)
                .hasMessage("Immeuble " + immeubleId + " does not belong to project " + projectId);
    }

    private PropertyCreateRequest validCreateRequest(UUID requestProjectId, UUID immeubleId) {
        return new PropertyCreateRequest(
                PropertyType.VILLA,
                "Luxury Villa",
                "VIL-001",
                new java.math.BigDecimal("5000000.00"),
                "MAD",
                null,
                null,
                "123 Palm Avenue",
                "Casablanca",
                "Grand Casablanca",
                "20000",
                null,
                null,
                null,
                null,
                null,
                null,
                new java.math.BigDecimal("350.00"),
                new java.math.BigDecimal("800.00"),
                5,
                4,
                2,
                3,
                true,
                true,
                2020,
                null,
                null,
                null,
                "Villa with beautiful garden and pool",
                null,
                null,
                requestProjectId,
                immeubleId,
                null
        );
    }

    private void stubMockProject(UUID id, String name) {
        when(mockProject.getId()).thenReturn(id);
        when(mockProject.getName()).thenReturn(name);
    }
}
