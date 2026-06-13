package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.common.event.VenteAnnuleeEvent;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.domain.MoyenRemboursement;
import com.yem.hlm.backend.vente.domain.Remboursement;
import com.yem.hlm.backend.vente.domain.StatutRemboursement;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.RemboursementRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemboursementServiceTest {

    @Mock private RemboursementRepository remboursementRepository;
    @Mock private VenteRepository venteRepository;
    @Mock private CommercialAuditService auditService;
    @Mock private SocieteContextHelper societeCtx;

    private RemboursementService service;

    private final UUID SOC   = UUID.randomUUID();
    private final UUID USER  = UUID.randomUUID();
    private final UUID VENTE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RemboursementService(remboursementRepository, venteRepository, auditService, societeCtx);
        lenient().when(remboursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(societeCtx.requireUserId()).thenReturn(USER);
        lenient().when(societeCtx.requireSocieteId()).thenReturn(SOC);
    }

    @Test
    @DisplayName("Cancellation event auto-creates a DU refund with the deposit amount + audits it")
    void onVenteAnnulee_createsDu() {
        when(remboursementRepository.existsByVenteId(VENTE)).thenReturn(false);

        service.onVenteAnnulee(new VenteAnnuleeEvent(SOC, USER, VENTE, new BigDecimal("25000")));

        ArgumentCaptor<Remboursement> captor = ArgumentCaptor.forClass(Remboursement.class);
        verify(remboursementRepository).save(captor.capture());
        assertThat(captor.getValue().getMontant()).isEqualByComparingTo("25000");
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutRemboursement.DU);
        verify(auditService).record(eq(SOC), eq(AuditEventType.REMBOURSEMENT_DU), eq(USER),
                eq("VENTE"), eq(VENTE), any());
    }

    @Test
    @DisplayName("Auto-create is idempotent — a second event does nothing")
    void onVenteAnnulee_idempotent() {
        when(remboursementRepository.existsByVenteId(VENTE)).thenReturn(true);

        service.onVenteAnnulee(new VenteAnnuleeEvent(SOC, USER, VENTE, new BigDecimal("25000")));

        verify(remboursementRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Null deposit auto-creates a DU of 0 (obligation still visible)")
    void onVenteAnnulee_nullDeposit_zero() {
        when(remboursementRepository.existsByVenteId(VENTE)).thenReturn(false);

        service.onVenteAnnulee(new VenteAnnuleeEvent(SOC, USER, VENTE, null));

        ArgumentCaptor<Remboursement> captor = ArgumentCaptor.forClass(Remboursement.class);
        verify(remboursementRepository).save(captor.capture());
        assertThat(captor.getValue().getMontant()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("upsertDu adjusts the amount on a cancelled vente")
    void upsertDu_adjustsAmount() {
        Vente vente = mock(Vente.class);
        when(vente.getStatut()).thenReturn(VenteStatut.ANNULE);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        Remboursement existing = new Remboursement(SOC, VENTE, new BigDecimal("0"), "auto", USER);
        when(remboursementRepository.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.of(existing));

        Remboursement result = service.upsertDu(VENTE, new BigDecimal("30000"), "Solde dépôt");

        assertThat(result.getMontant()).isEqualByComparingTo("30000");
        assertThat(result.getMotif()).isEqualTo("Solde dépôt");
    }

    @Test
    @DisplayName("upsertDu rejects a non-cancelled vente")
    void upsertDu_rejectsActiveVente() {
        Vente vente = mock(Vente.class);
        when(vente.getStatut()).thenReturn(VenteStatut.COMPROMIS);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));

        assertThatThrownBy(() -> service.upsertDu(VENTE, new BigDecimal("100"), null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").hasToString("INVALID_REQUEST");
    }

    @Test
    @DisplayName("marquerEffectue sets statut/date/moyen and audits REMBOURSEMENT_EFFECTUE")
    void marquerEffectue_paysAndAudits() {
        Remboursement remb = new Remboursement(SOC, VENTE, new BigDecimal("25000"), "auto", USER);
        when(remboursementRepository.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.of(remb));

        Remboursement result = service.marquerEffectue(
                VENTE, LocalDate.of(2026, 6, 20), MoyenRemboursement.VIREMENT, "VIR-001");

        assertThat(result.getStatut()).isEqualTo(StatutRemboursement.EFFECTUE);
        assertThat(result.getDateRemboursement()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(result.getMoyen()).isEqualTo(MoyenRemboursement.VIREMENT);
        assertThat(result.getReference()).isEqualTo("VIR-001");
        verify(auditService).record(eq(SOC), eq(AuditEventType.REMBOURSEMENT_EFFECTUE), eq(USER),
                eq("VENTE"), eq(VENTE), any());
    }

    @Test
    @DisplayName("marquerEffectue on an already-paid refund is rejected")
    void marquerEffectue_alreadyPaid() {
        Remboursement remb = new Remboursement(SOC, VENTE, new BigDecimal("25000"), "auto", USER);
        remb.setStatut(StatutRemboursement.EFFECTUE);
        when(remboursementRepository.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.of(remb));

        assertThatThrownBy(() -> service.marquerEffectue(VENTE, null, MoyenRemboursement.CHEQUE, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("get throws NOT_FOUND when no refund exists")
    void get_notFound() {
        when(remboursementRepository.findBySocieteIdAndVenteId(SOC, VENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(VENTE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").hasToString("NOT_FOUND");
    }
}
