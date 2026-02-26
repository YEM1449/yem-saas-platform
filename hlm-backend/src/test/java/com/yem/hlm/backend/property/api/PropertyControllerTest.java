package com.yem.hlm.backend.property.api;

import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.domain.PropertyCategory;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PropertyController focusing on endpoint contract and parameter passing.
 * Uses pure Mockito without Spring context for deterministic, fast testing.
 */
@ExtendWith(MockitoExtension.class)
class PropertyControllerTest {

    @Mock
    private PropertyService propertyService;

    @InjectMocks
    private PropertyController propertyController;

    private PropertyResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = createMockPropertyResponse();
    }

    @Test
    void list_noQueryParams_callsServiceWithNullFilters() {
        // Given
        when(propertyService.listAll(null, null)).thenReturn(List.of(mockResponse));

        // When
        List<PropertyResponse> result = propertyController.list(null, null);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(mockResponse.id());
        verify(propertyService).listAll(null, null);
    }

    @Test
    void list_withTypeParam_callsServiceWithType() {
        // Given
        when(propertyService.listAll(PropertyType.VILLA, null)).thenReturn(List.of(mockResponse));

        // When
        List<PropertyResponse> result = propertyController.list(PropertyType.VILLA, null);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyService).listAll(PropertyType.VILLA, null);
    }

    @Test
    void list_withStatusParam_callsServiceWithStatus() {
        // Given
        when(propertyService.listAll(null, PropertyStatus.ACTIVE)).thenReturn(List.of(mockResponse));

        // When
        List<PropertyResponse> result = propertyController.list(null, PropertyStatus.ACTIVE);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyService).listAll(null, PropertyStatus.ACTIVE);
    }

    @Test
    void list_withBothParams_callsServiceWithBothFilters() {
        // Given
        when(propertyService.listAll(PropertyType.VILLA, PropertyStatus.ACTIVE))
                .thenReturn(List.of(mockResponse));

        // When
        List<PropertyResponse> result = propertyController.list(PropertyType.VILLA, PropertyStatus.ACTIVE);

        // Then
        assertThat(result).hasSize(1);
        verify(propertyService).listAll(PropertyType.VILLA, PropertyStatus.ACTIVE);
    }

    private PropertyResponse createMockPropertyResponse() {
        return new PropertyResponse(
                UUID.randomUUID(), // id
                PropertyType.VILLA, // type
                PropertyCategory.VILLA, // category
                PropertyStatus.DRAFT, // status
                "TEST-001", // referenceCode
                "Test Villa", // title
                "Test description", // description
                null, // notes
                new BigDecimal("1000000"), // price
                "MAD", // currency
                null, // commissionRate
                null, // estimatedValue
                null, // address
                null, // city
                null, // region
                null, // postalCode
                null, // latitude
                null, // longitude
                null, // titleDeedNumber
                null, // cadastralReference
                null, // ownerName
                null, // legalStatus
                new BigDecimal("200"), // surfaceAreaSqm
                new BigDecimal("400"), // landAreaSqm
                3, // bedrooms
                2, // bathrooms
                1, // floors
                2, // parkingSpaces
                true, // hasGarden
                false, // hasPool
                2020, // buildingYear
                null, // floorNumber
                null, // zoning
                null, // isServiced
                false, // listedForSale
                UUID.randomUUID(), // projectId
                "Test Project", // projectName
                null, // buildingName
                UUID.randomUUID(), // createdBy
                UUID.randomUUID(), // updatedBy
                java.time.LocalDateTime.now(), // createdAt
                java.time.LocalDateTime.now(), // updatedAt
                null, // deletedAt
                null, // publishedAt
                null // soldAt
        );
    }
}
