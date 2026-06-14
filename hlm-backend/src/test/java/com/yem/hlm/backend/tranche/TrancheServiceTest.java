package com.yem.hlm.backend.tranche;

import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.api.dto.UpdateTrancheStatutRequest;
import com.yem.hlm.backend.tranche.domain.Tranche;
import com.yem.hlm.backend.tranche.domain.TrancheStatut;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.tranche.service.InvalidTrancheTransitionException;
import com.yem.hlm.backend.tranche.service.TrancheNotFoundException;
import com.yem.hlm.backend.tranche.service.TrancheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrancheServiceTest {

    private static final UUID SOC      = UUID.randomUUID();
    private static final UUID PROJECT  = UUID.randomUUID();
    private static final UUID TRANCHE  = UUID.randomUUID();

    @Mock TrancheRepository trancheRepo;

    private TrancheService service;

    @BeforeEach
    void setUp() {
        SocieteContext.setSocieteId(SOC);
        service = new TrancheService(trancheRepo);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    @Test
    @DisplayName("advanceStatut → 404 when tranche not found")
    void advanceStatut_notFound() {
        when(trancheRepo.findBySocieteIdAndId(SOC, TRANCHE)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.advanceStatut(PROJECT, TRANCHE, new UpdateTrancheStatutRequest(TrancheStatut.EN_COMMERCIALISATION)))
                .isInstanceOf(TrancheNotFoundException.class);
    }

    @Test
    @DisplayName("advanceStatut → 409 when transition skips a stage (EN_PREPARATION → EN_TRAVAUX)")
    void advanceStatut_skippingStage_rejected() {
        Tranche t = mockTranche(PROJECT, TrancheStatut.EN_PREPARATION);
        when(trancheRepo.findBySocieteIdAndId(SOC, TRANCHE)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
                service.advanceStatut(PROJECT, TRANCHE, new UpdateTrancheStatutRequest(TrancheStatut.EN_TRAVAUX)))
                .isInstanceOf(InvalidTrancheTransitionException.class);
    }

    @Test
    @DisplayName("advanceStatut → 409 when trying to go backward (EN_COMMERCIALISATION → EN_PREPARATION)")
    void advanceStatut_backward_rejected() {
        Tranche t = mockTranche(PROJECT, TrancheStatut.EN_COMMERCIALISATION);
        when(trancheRepo.findBySocieteIdAndId(SOC, TRANCHE)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
                service.advanceStatut(PROJECT, TRANCHE, new UpdateTrancheStatutRequest(TrancheStatut.EN_PREPARATION)))
                .isInstanceOf(InvalidTrancheTransitionException.class);
    }

    @Test
    @DisplayName("advanceStatut → 409 when tranche belongs to a different project")
    void advanceStatut_wrongProject_notFound() {
        UUID otherProject = UUID.randomUUID();
        Tranche t = mockTranche(otherProject, TrancheStatut.EN_PREPARATION);
        when(trancheRepo.findBySocieteIdAndId(SOC, TRANCHE)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
                service.advanceStatut(PROJECT, TRANCHE, new UpdateTrancheStatutRequest(TrancheStatut.EN_COMMERCIALISATION)))
                .isInstanceOf(TrancheNotFoundException.class);
    }

    @Test
    @DisplayName("advanceStatut happy path: EN_PREPARATION → EN_COMMERCIALISATION saves the tranche")
    void advanceStatut_forward_saves() {
        Tranche t = mockTranche(PROJECT, TrancheStatut.EN_PREPARATION);
        when(trancheRepo.findBySocieteIdAndId(SOC, TRANCHE)).thenReturn(Optional.of(t));
        when(trancheRepo.save(t)).thenReturn(t);
        when(trancheRepo.countBuildings(SOC, TRANCHE)).thenReturn(0);
        when(trancheRepo.countUnits(SOC, TRANCHE)).thenReturn(0);
        when(trancheRepo.countUnitsByStatus(SOC, TRANCHE)).thenReturn(List.of());

        service.advanceStatut(PROJECT, TRANCHE, new UpdateTrancheStatutRequest(TrancheStatut.EN_COMMERCIALISATION));

        verify(trancheRepo).save(t);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Tranche mockTranche(UUID projectId, TrancheStatut statut) {
        Tranche t = mock(Tranche.class);
        lenient().when(t.getId()).thenReturn(TRANCHE);
        lenient().when(t.getProjectId()).thenReturn(projectId);
        lenient().when(t.getStatut()).thenReturn(statut);
        return t;
    }
}
