package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.deposit.service.pdf.DocumentGenerationService;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.vente.api.dto.GeneratedDocumentResponse;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteDocument;
import com.yem.hlm.backend.vente.domain.VenteDocumentType;
import com.yem.hlm.backend.vente.repo.ReserveLivraisonRepository;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VenteLegalDocumentServiceTest {

    private static final UUID SOC   = UUID.randomUUID();
    private static final UUID VENTE = UUID.randomUUID();
    private static final UUID PROP  = UUID.randomUUID();

    @Mock VenteRepository venteRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock SocieteRepository societeRepository;
    @Mock ReserveLivraisonRepository reserveRepository;
    @Mock com.yem.hlm.backend.vente.repo.VenteEcheanceRepository echeanceRepository;
    @Mock VenteDocumentRepository documentRepository;
    @Mock DocumentGenerationService pdf;
    @Mock MediaStorageService storage;
    @Mock SocieteContextHelper societeCtx;

    private VenteLegalDocumentService service() {
        return new VenteLegalDocumentService(venteRepository, propertyRepository, societeRepository,
                reserveRepository, echeanceRepository, documentRepository, pdf, storage, societeCtx);
    }

    @Test
    @DisplayName("contrat de réservation is rendered, stored, and saved as a CONTRAT_RESERVATION document")
    void generateContratReservation_storesTypedDocument() throws IOException {
        Contact contact = mock(Contact.class);
        when(contact.getFullName()).thenReturn("Jean Dupont");
        when(contact.getNationalId()).thenReturn("AB123456");
        Vente vente = mock(Vente.class);
        when(vente.getContact()).thenReturn(contact);
        when(vente.getPropertyId()).thenReturn(PROP);
        when(vente.getPrixVente()).thenReturn(new BigDecimal("900000"));
        when(vente.getVenteRef()).thenReturn("VTE-2026-ABC-00001");

        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        when(propertyRepository.findBySocieteIdAndId(SOC, PROP)).thenReturn(Optional.empty());
        Societe societe = mock(Societe.class);
        when(societe.getNom()).thenReturn("ACME Immobilier");
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societe));
        when(pdf.renderToPdf(anyString(), any())).thenReturn(new byte[]{1, 2, 3});
        when(storage.store(any(byte[].class), anyString(), anyString())).thenReturn("models/key.pdf");
        when(documentRepository.save(any(VenteDocument.class))).thenAnswer(i -> i.getArgument(0));

        GeneratedDocumentResponse resp = service().generateContratReservation(VENTE);

        assertThat(resp.documentType()).isEqualTo(VenteDocumentType.CONTRAT_RESERVATION);
        assertThat(resp.nomFichier()).startsWith("contrat-reservation-");

        ArgumentCaptor<VenteDocument> captor = ArgumentCaptor.forClass(VenteDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentType()).isEqualTo(VenteDocumentType.CONTRAT_RESERVATION);
        verify(pdf).renderToPdf(eqTemplate(), any());
    }

    private static String eqTemplate() {
        return org.mockito.ArgumentMatchers.eq("documents/contrat-reservation-vefa");
    }

    @Test
    @DisplayName("quittance is generated for a PAID échéance and saved as a QUITTANCE document")
    void generateQuittance_forPaidEcheance() throws IOException {
        UUID echeanceId = UUID.randomUUID();
        Contact contact = mock(Contact.class);
        when(contact.getFullName()).thenReturn("Salma Bennani");
        Vente vente = mock(Vente.class);
        when(vente.getId()).thenReturn(VENTE);
        when(vente.getContact()).thenReturn(contact);
        when(vente.getPropertyId()).thenReturn(PROP);
        when(vente.getVenteRef()).thenReturn("VTE-2026-ABC-00001");

        var echeance = mock(com.yem.hlm.backend.vente.domain.VenteEcheance.class);
        when(echeance.getVente()).thenReturn(vente);
        when(echeance.getStatut()).thenReturn(com.yem.hlm.backend.vente.domain.EcheanceStatut.PAYEE);
        when(echeance.getLibelle()).thenReturn("Appel fondations");
        when(echeance.getMontant()).thenReturn(new BigDecimal("90000"));

        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        when(echeanceRepository.findBySocieteIdAndId(SOC, echeanceId)).thenReturn(Optional.of(echeance));
        when(propertyRepository.findBySocieteIdAndId(SOC, PROP)).thenReturn(Optional.empty());
        Societe societe = mock(Societe.class);
        when(societe.getNom()).thenReturn("ACME Immobilier");
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societe));
        when(pdf.renderToPdf(anyString(), any())).thenReturn(new byte[]{4, 5, 6});
        when(storage.store(any(byte[].class), anyString(), anyString())).thenReturn("models/quittance.pdf");
        when(documentRepository.save(any(VenteDocument.class))).thenAnswer(i -> i.getArgument(0));

        GeneratedDocumentResponse resp = service().generateQuittance(VENTE, echeanceId);

        assertThat(resp.documentType()).isEqualTo(VenteDocumentType.QUITTANCE);
        verify(pdf).renderToPdf(org.mockito.ArgumentMatchers.eq("documents/quittance-vefa"), any());
        ArgumentCaptor<VenteDocument> captor = ArgumentCaptor.forClass(VenteDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getDocumentType()).isEqualTo(VenteDocumentType.QUITTANCE);
    }

    @Test
    @DisplayName("quittance is refused for an unpaid échéance (422)")
    void generateQuittance_refusedWhenNotPaid() {
        UUID echeanceId = UUID.randomUUID();
        Vente vente = mock(Vente.class);
        when(vente.getId()).thenReturn(VENTE);
        var echeance = mock(com.yem.hlm.backend.vente.domain.VenteEcheance.class);
        when(echeance.getVente()).thenReturn(vente);
        when(echeance.getStatut()).thenReturn(com.yem.hlm.backend.vente.domain.EcheanceStatut.EN_ATTENTE);

        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        when(echeanceRepository.findBySocieteIdAndId(SOC, echeanceId)).thenReturn(Optional.of(echeance));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().generateQuittance(VENTE, echeanceId))
                .isInstanceOf(ViolationLegaleException.class);
        verify(documentRepository, org.mockito.Mockito.never()).save(any());
    }
}
