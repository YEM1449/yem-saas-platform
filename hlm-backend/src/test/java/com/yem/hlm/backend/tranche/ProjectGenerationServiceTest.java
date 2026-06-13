package com.yem.hlm.backend.tranche;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.immeuble.repo.ImmeubleRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNameAlreadyExistsException;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.api.dto.ProjectGenerationRequest;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.tranche.service.ProjectGenerationService;
import com.yem.hlm.backend.societe.QuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationServiceTest {

    private static final UUID SOC  = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock ProjectRepository  projectRepo;
    @Mock TrancheRepository  trancheRepo;
    @Mock ImmeubleRepository immeubleRepo;
    @Mock PropertyRepository propertyRepo;
    @Mock JdbcTemplate       jdbc;
    @Mock CacheManager       cacheManager;
    @Mock QuotaService       quotaService;

    private ProjectGenerationService service;

    @BeforeEach
    void setUp() {
        SocieteContext.setSocieteId(SOC);
        SocieteContext.setUserId(USER);
        service = new ProjectGenerationService(
                projectRepo, trancheRepo, immeubleRepo, propertyRepo,
                jdbc, cacheManager, quotaService);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    @Test
    @DisplayName("generate() → 409 when project name already exists for the société")
    void generate_duplicateName_rejected() {
        when(projectRepo.existsBySocieteIdAndName(SOC, "Résidence Soleil"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.generate(minimalRequest("Résidence Soleil")))
                .isInstanceOf(ProjectNameAlreadyExistsException.class);

        verify(quotaService).enforceProjectQuota(SOC);
        verify(quotaService).enforceBienQuota(SOC);
    }

    @Test
    @DisplayName("generate() → quota check runs before name validation")
    void generate_quotaEnforcedFirst() {
        doThrow(new RuntimeException("QUOTA_PROJETS_ATTEINT"))
                .when(quotaService).enforceProjectQuota(SOC);

        assertThatThrownBy(() -> service.generate(minimalRequest("Projet X")))
                .hasMessageContaining("QUOTA_PROJETS_ATTEINT");

        verify(projectRepo, never()).existsBySocieteIdAndName(any(), any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ProjectGenerationRequest minimalRequest(String name) {
        return new ProjectGenerationRequest(
                name, null, null, "Casablanca", null,
                null, null, null, null, null,
                "LETTRE", "Bâtiment", "BUILDING_FLOOR_UNIT", "APT", "RDC",
                false, null, false,
                List.of());
    }
}
