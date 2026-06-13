package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.api.dto.DossierFinancementResponse;
import com.yem.hlm.backend.vente.api.dto.UpsertDossierFinancementRequest;
import com.yem.hlm.backend.vente.domain.DossierFinancement;
import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import com.yem.hlm.backend.vente.domain.TypeFinancement;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.repo.DossierFinancementRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierFinancementServiceTest {

    private static final UUID SOC   = UUID.randomUUID();
    private static final UUID VENTE = UUID.randomUUID();

    @Mock DossierFinancementRepository repo;
    @Mock VenteRepository venteRepository;
    @Mock SocieteContextHelper societeCtx;

    private DossierFinancementService service() {
        return new DossierFinancementService(repo, venteRepository, societeCtx);
    }

    @Test
    @DisplayName("getByVente → 404 when there is no financing file")
    void getByVente_notFound() {
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(repo.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getByVente(VENTE))
                .isInstanceOf(DossierFinancementNotFoundException.class);
    }

    @Test
    @DisplayName("upsert creates the file on first call and applies the fields")
    void upsert_createsWhenNone() {
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(mock(Vente.class)));
        when(repo.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.empty());
        when(repo.save(any(DossierFinancement.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpsertDossierFinancementRequest(
                TypeFinancement.CREDIT_IMMOBILIER, "CIH", new BigDecimal("800000"),
                new BigDecimal("0.0450"), 240, new BigDecimal("200000"),
                StatutDossierFinancement.ACCORD_PRINCIPE, null, null, null, "RAS");

        DossierFinancementResponse resp = service().upsert(VENTE, req);

        assertThat(resp.banque()).isEqualTo("CIH");
        assertThat(resp.statut()).isEqualTo(StatutDossierFinancement.ACCORD_PRINCIPE);
        assertThat(resp.montantCredit()).isEqualByComparingTo("800000");
    }
}
