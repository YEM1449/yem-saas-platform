package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.InvalidPropertyStatusTransitionException;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.vente.api.dto.CreateVenteRequest;
import com.yem.hlm.backend.vente.api.dto.UpdateVenteStatutRequest;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VenteService} business rules — Docker-free (pure Mockito).
 *
 * <p>Focus: the rules that must hold before any persistence happens, so the tests
 * never reach {@code save()}/{@code toResponse()} and stay robust:
 * <ul>
 *   <li>RG-B03 — at most one active (non-cancelled) vente per property.</li>
 *   <li>RG-B04 — the vente state machine (valid/invalid transitions).</li>
 *   <li>Property status precondition for starting a vente.</li>
 *   <li>Cancellation requires a motif.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VenteServiceTest {

    private static final UUID SOC     = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER    = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONTACT = UUID.randomUUID();
    private static final UUID PROP    = UUID.randomUUID();
    private static final UUID AGENT   = UUID.randomUUID();
    private static final UUID VENTE   = UUID.randomUUID();

    @Mock VenteRepository venteRepository;
    @Mock VenteEcheanceRepository echeanceRepository;
    @Mock VenteDocumentRepository documentRepository;
    @Mock ContactRepository contactRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock UserRepository userRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock PropertyCommercialWorkflowService propertyWorkflow;
    @Mock SocieteContextHelper societeCtx;
    @Mock DateCoherenceValidator dateCoherence;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock VenteRefGenerator refGenerator;
    @Mock com.yem.hlm.backend.legal.MarketConfig marketConfig;

    private VenteService service;

    @BeforeEach
    void setUp() {
        service = new VenteService(
                venteRepository, echeanceRepository, documentRepository, contactRepository,
                propertyRepository, userRepository, reservationRepository, propertyWorkflow,
                societeCtx, dateCoherence, eventPublisher, refGenerator, marketConfig);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CreateVenteRequest directRequest() {
        return new CreateVenteRequest(
                null, CONTACT, PROP, AGENT,
                BigDecimal.valueOf(100_000), null,
                LocalDate.now(), null, null, "notes");
    }

    private void stubDirectCreationLookups(PropertyStatus propStatus) {
        when(societeCtx.requireUserId()).thenReturn(USER);
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        Contact contact = org.mockito.Mockito.mock(Contact.class);
        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT)).thenReturn(Optional.of(contact));
        Property property = org.mockito.Mockito.mock(Property.class);
        lenient().when(property.getId()).thenReturn(PROP);
        lenient().when(property.getStatus()).thenReturn(propStatus);
        when(propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(SOC, PROP))
                .thenReturn(Optional.of(property));
        User agent = org.mockito.Mockito.mock(User.class);
        when(userRepository.findById(AGENT)).thenReturn(Optional.of(agent));
    }

    /** Lookups for createOption() up to the RG-B03/status guards (no agent resolution — guards fail first). */
    private void stubOptionLookups(PropertyStatus propStatus) {
        when(societeCtx.requireUserId()).thenReturn(USER);
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Contact.class)));
        Property property = org.mockito.Mockito.mock(Property.class);
        lenient().when(property.getStatus()).thenReturn(propStatus);
        when(propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(SOC, PROP))
                .thenReturn(Optional.of(property));
    }

    // ── RG-B03 : one active vente per property ────────────────────────────────

    @Test
    @DisplayName("RG-B03: create() rejects a 2nd vente on a property with an active vente → 409")
    void create_rejectsDuplicateActiveVente() {
        stubDirectCreationLookups(PropertyStatus.RESERVED);
        when(venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(directRequest()))
                .isInstanceOf(PropertyAlreadyEngagedException.class);

        // Guard must fire before any persistence / contact mutation.
        verify(venteRepository, never()).save(any());
        verify(contactRepository, never()).save(any());
        verify(propertyRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-B03 guard excludes only ANNULE (a cancelled vente frees the property)")
    void create_guardChecksNonCancelledVente() {
        stubDirectCreationLookups(PropertyStatus.ACTIVE);
        when(venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(directRequest()))
                .isInstanceOf(PropertyAlreadyEngagedException.class);

        verify(venteRepository).existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE);
    }

    @Test
    @DisplayName("create() rejects a vente on a WITHDRAWN property (not ACTIVE/RESERVED)")
    void create_rejectsInvalidPropertyStatus() {
        stubDirectCreationLookups(PropertyStatus.WITHDRAWN);
        when(venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(directRequest()))
                .isInstanceOf(InvalidPropertyStatusTransitionException.class);

        verify(venteRepository, never()).save(any());
    }

    // ── RG-B04 : vente state machine ──────────────────────────────────────────

    private Vente venteWithStatut(VenteStatut statut) {
        Vente vente = org.mockito.Mockito.mock(Vente.class);
        when(vente.getStatut()).thenReturn(statut);
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        return vente;
    }

    private UpdateVenteStatutRequest statutRequest(VenteStatut target) {
        return new UpdateVenteStatutRequest(target, null, null, null, null, null);
    }

    @Test
    @DisplayName("RG-B04: COMPROMIS → LIVRE is rejected (must go through the pipeline)")
    void updateStatut_rejectsSkippingStages() {
        Vente vente = venteWithStatut(VenteStatut.COMPROMIS);

        assertThatThrownBy(() -> service.updateStatut(VENTE, statutRequest(VenteStatut.LIVRE_DEFINITIF)))
                .isInstanceOf(InvalidVenteTransitionException.class);

        verify(vente, never()).setStatut(any());
    }

    @Test
    @DisplayName("RG-B04: no transition out of terminal LIVRE")
    void updateStatut_rejectsTransitionFromTerminal() {
        Vente vente = venteWithStatut(VenteStatut.LIVRE_DEFINITIF);

        assertThatThrownBy(() -> service.updateStatut(VENTE, statutRequest(VenteStatut.FINANCEMENT)))
                .isInstanceOf(InvalidVenteTransitionException.class);

        verify(vente, never()).setStatut(any());
    }

    @Test
    @DisplayName("Cancellation requires a motif (ANNULE allowed from FINANCEMENT, but motif mandatory)")
    void updateStatut_annuleRequiresMotif() {
        Vente vente = venteWithStatut(VenteStatut.FINANCEMENT);

        // FINANCEMENT → ANNULE is a *valid* transition, so it passes validateTransition()
        // and fails on the missing motif — proving both the allowed edge and the guard.
        assertThatThrownBy(() -> service.updateStatut(VENTE, statutRequest(VenteStatut.ANNULE)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vente, never()).setStatut(any());
    }

    // ── VEFA Loi 44-00 (OPTION + rétractation) ───────────────────────────────

    @Test
    @DisplayName("createOption rejects a property that already has an active vente (RG-B03)")
    void createOption_rejectsDuplicateActiveVente() {
        stubOptionLookups(PropertyStatus.ACTIVE);
        when(venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createOption(PROP, CONTACT, 48))
                .isInstanceOf(PropertyAlreadyEngagedException.class);
        verify(venteRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOption requires the property to be ACTIVE")
    void createOption_requiresActiveProperty() {
        stubOptionLookups(PropertyStatus.RESERVED);
        when(venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(SOC, PROP, VenteStatut.ANNULE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createOption(PROP, CONTACT, 48))
                .isInstanceOf(InvalidPropertyStatusTransitionException.class);
        verify(venteRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmReservation rejects a deposit above the 5% legal cap (Art. 618-4)")
    void confirmReservation_rejectsExcessiveDeposit() {
        Vente vente = org.mockito.Mockito.mock(Vente.class);
        when(vente.getStatut()).thenReturn(VenteStatut.OPTION);
        when(vente.getPrixVente()).thenReturn(new java.math.BigDecimal("100000"));
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(venteRepository.findBySocieteIdAndId(SOC, VENTE)).thenReturn(Optional.of(vente));
        when(marketConfig.getDepotGarantieMaxPct()).thenReturn(new java.math.BigDecimal("0.05"));

        // 10 000 > 5 000 (5% of 100 000) → legal violation
        assertThatThrownBy(() -> service.confirmReservation(VENTE, new java.math.BigDecimal("10000")))
                .isInstanceOf(ViolationLegaleException.class);
        verify(vente, never()).setStatut(any());
    }

    @Test
    @DisplayName("exerciseRetractation rejected when the vente is not in the cooling-off period")
    void exerciseRetractation_rejectedWhenNotInWindow() {
        venteWithStatut(VenteStatut.COMPROMIS);

        assertThatThrownBy(() -> service.exerciseRetractation(VENTE))
                .isInstanceOf(RetractationImpossibleException.class);
    }

    @Test
    @DisplayName("exerciseRetractation rejected after the legal deadline has passed")
    void exerciseRetractation_rejectedAfterDeadline() {
        Vente vente = venteWithStatut(VenteStatut.EN_RETRACTATION);
        when(vente.getDateFinDelaiReflexion()).thenReturn(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.exerciseRetractation(VENTE))
                .isInstanceOf(RetractationImpossibleException.class);
        verify(vente, never()).setStatut(VenteStatut.ANNULE);
    }
}
