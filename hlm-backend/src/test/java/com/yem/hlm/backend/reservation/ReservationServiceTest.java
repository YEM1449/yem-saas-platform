package com.yem.hlm.backend.reservation;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.notification.service.NotificationService;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.reservation.api.dto.CancelReservationRequest;
import com.yem.hlm.backend.reservation.api.dto.CreateReservationRequest;
import com.yem.hlm.backend.reservation.domain.Reservation;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.reservation.service.InvalidReservationStateException;
import com.yem.hlm.backend.reservation.service.PropertyNotAvailableForReservationException;
import com.yem.hlm.backend.reservation.service.ReservationRefGenerator;
import com.yem.hlm.backend.reservation.service.ReservationService;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID SOC      = UUID.randomUUID();
    private static final UUID USER     = UUID.randomUUID();
    private static final UUID CONTACT  = UUID.randomUUID();
    private static final UUID PROPERTY = UUID.randomUUID();
    private static final UUID RES_ID   = UUID.randomUUID();

    @Mock ReservationRepository reservationRepository;
    @Mock ContactRepository contactRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock UserRepository userRepository;
    @Mock PropertyCommercialWorkflowService propertyWorkflow;
    @Mock DepositService depositService;
    @Mock CommercialAuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ReservationRefGenerator refGenerator;
    @Mock VenteRepository venteRepository;
    @Mock TrancheRepository trancheRepository;
    @Mock NotificationService notificationService;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        SocieteContext.setSocieteId(SOC);
        SocieteContext.setUserId(USER);
        service = new ReservationService(
                reservationRepository, contactRepository, propertyRepository,
                userRepository, propertyWorkflow, depositService, auditService,
                eventPublisher, refGenerator, venteRepository, trancheRepository,
                notificationService);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    @Test
    @DisplayName("create() → 409 when property is already RESERVED (not ACTIVE)")
    void create_propertyAlreadyReserved() {
        Contact contact = mock(Contact.class);
        User agent = mock(User.class);
        Property property = mock(Property.class);
        when(property.getStatus()).thenReturn(PropertyStatus.RESERVED);

        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT)).thenReturn(Optional.of(contact));
        when(userRepository.findById(USER)).thenReturn(Optional.of(agent));
        when(propertyRepository.findBySocieteIdAndIdForUpdate(SOC, PROPERTY)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> service.create(
                new CreateReservationRequest(CONTACT, PROPERTY, null, null, null)))
                .isInstanceOf(PropertyNotAvailableForReservationException.class);
    }

    @Test
    @DisplayName("create() → 409 when property is SOLD")
    void create_propertySold_rejected() {
        Contact contact = mock(Contact.class);
        User agent = mock(User.class);
        Property property = mock(Property.class);
        when(property.getStatus()).thenReturn(PropertyStatus.SOLD);

        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT)).thenReturn(Optional.of(contact));
        when(userRepository.findById(USER)).thenReturn(Optional.of(agent));
        when(propertyRepository.findBySocieteIdAndIdForUpdate(SOC, PROPERTY)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> service.create(
                new CreateReservationRequest(CONTACT, PROPERTY, null, null, null)))
                .isInstanceOf(PropertyNotAvailableForReservationException.class);
    }

    @Test
    @DisplayName("cancel() → 409 when reservation is already EXPIRED")
    void cancel_expiredReservation_rejected() {
        Reservation res = mockReservation(ReservationStatus.EXPIRED);
        when(reservationRepository.findBySocieteIdAndId(SOC, RES_ID)).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service.cancel(RES_ID, new CancelReservationRequest("Test")))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    @Test
    @DisplayName("cancel() → 409 when reservation is already CANCELLED")
    void cancel_alreadyCancelled_rejected() {
        Reservation res = mockReservation(ReservationStatus.CANCELLED);
        when(reservationRepository.findBySocieteIdAndId(SOC, RES_ID)).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service.cancel(RES_ID, new CancelReservationRequest("Test")))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    @Test
    @DisplayName("cancel() → 409 when reservation is already CONVERTED_TO_DEPOSIT")
    void cancel_convertedReservation_rejected() {
        Reservation res = mockReservation(ReservationStatus.CONVERTED_TO_DEPOSIT);
        when(reservationRepository.findBySocieteIdAndId(SOC, RES_ID)).thenReturn(Optional.of(res));

        assertThatThrownBy(() -> service.cancel(RES_ID, new CancelReservationRequest("Test")))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Reservation mockReservation(ReservationStatus status) {
        Reservation r = mock(Reservation.class);
        when(r.getStatus()).thenReturn(status);
        return r;
    }
}
