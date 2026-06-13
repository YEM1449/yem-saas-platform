package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.api.dto.UpsertCoAcquereurRequest;
import com.yem.hlm.backend.vente.domain.CoAcquereur;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.repo.CoAcquereurRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoAcquereurServiceTest {

    private static final UUID SOC   = UUID.randomUUID();
    private static final UUID VENTE = UUID.randomUUID();
    private static final UUID CO    = UUID.randomUUID();

    @Mock CoAcquereurRepository repo;
    @Mock VenteRepository venteRepository;
    @Mock SocieteContextHelper societeCtx;

    private CoAcquereurService service() {
        return new CoAcquereurService(repo, venteRepository, societeCtx);
    }

    private UpsertCoAcquereurRequest req() {
        return new UpsertCoAcquereurRequest("Bennani", "Salma",
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("add rejects a second co-buyer on a vente (Wave 12: one per vente)")
    void add_rejectsSecondCoAcquereur() {
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(mock(Vente.class)));
        when(repo.existsBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(true);

        assertThatThrownBy(() -> service().add(VENTE, req()))
                .isInstanceOf(CoAcquereurAlreadyExistsException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("update → 404 when the co-buyer is not found")
    void update_notFound() {
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(repo.findBySocieteIdAndId(SOC, CO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(VENTE, CO, req()))
                .isInstanceOf(CoAcquereurNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("update → 404 when the co-buyer belongs to a different vente")
    void update_wrongVente() {
        CoAcquereur co = mock(CoAcquereur.class);
        lenient().when(co.getVenteId()).thenReturn(UUID.randomUUID()); // different vente
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(repo.findBySocieteIdAndId(SOC, CO)).thenReturn(Optional.of(co));

        assertThatThrownBy(() -> service().update(VENTE, CO, req()))
                .isInstanceOf(CoAcquereurNotFoundException.class);
        verify(repo, never()).save(any());
    }
}
