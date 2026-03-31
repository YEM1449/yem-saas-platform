package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.*;
import com.yem.hlm.backend.contact.repo.ContactInterestRepository;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.repo.ProspectDetailRepository;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.deposit.service.InvalidDepositRequestException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock ContactRepository contactRepository;
    @Mock ContactInterestRepository contactInterestRepository;
    @Mock SocieteRepository societeRepository;
    @Mock ProspectDetailRepository prospectDetailRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock DepositService depositService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CommercialAuditService auditService;

    @InjectMocks ContactService service;

    @BeforeEach
    void setUpSocieteContext() {
        SocieteContext.setSocieteId(TENANT_ID);
        SocieteContext.setUserId(UUID.randomUUID());

        Societe societe = mock(Societe.class);
        when(societe.getId()).thenReturn(TENANT_ID);
        when(societeRepository.findById(TENANT_ID)).thenReturn(Optional.of(societe));
    }

    @AfterEach
    void clearSocieteContext() {
        SocieteContext.clear();
    }

    @Test
    void create_defaultsToProspect() {
        when(contactRepository.existsBySocieteIdAndEmail(any(), any())).thenReturn(false);

        // Capture saved entity to verify status
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        ContactResponse res = service.create(new CreateContactRequest(
                "John", "Doe", "0612", "john@example.com", null, null, null, null, null, ProcessingBasis.CONTRACT
        ));

        assertThat(res.status()).isEqualTo(ContactStatus.PROSPECT);
        assertThat(res.firstName()).isEqualTo("John");
        assertThat(res.lastName()).isEqualTo("Doe");
    }

    @Test
    void convertToClient_invalidRequest_propagatesInvalidDepositRequest() {
        doThrow(new InvalidDepositRequestException("propertyId is required"))
                .when(depositService).createReservationForContact(any(), any());

        assertThatThrownBy(() -> service.convertToClient(UUID.randomUUID(),
                new ConvertToClientRequest(null, null, null, null, null, null)))
                .isInstanceOf(InvalidDepositRequestException.class);
    }

    @Test
    void convertToClient_delegatesToDepositService_andReturnsGet() {
        UUID contactId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        ContactResponse expected = new ContactResponse(
                contactId,
                ContactType.TEMP_CLIENT,
                ContactStatus.QUALIFIED_PROSPECT,
                true,
                null,
                "A",
                "B",
                "A B",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );

        ContactService spy = Mockito.spy(service);
        doReturn(expected).when(spy).get(contactId);

        ConvertToClientRequest req = new ConvertToClientRequest(
                propertyId,
                new BigDecimal("1000.00"),
                LocalDate.now(),
                "DEP",
                "MAD",
                null
        );

        ContactResponse res = spy.convertToClient(contactId, req);

        verify(depositService).createReservationForContact(contactId, req);
        assertThat(res).isEqualTo(expected);
    }

    @Test
    void addInterest_duplicate_throwsConflict() {
        UUID contactId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        when(contactRepository.findBySocieteIdAndId(TENANT_ID, contactId)).thenReturn(Optional.of(mock(Contact.class)));
        when(propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(TENANT_ID, propertyId)).thenReturn(Optional.of(mock(Property.class)));
        when(contactInterestRepository.existsBySocieteIdAndContactIdAndPropertyId(TENANT_ID, contactId, propertyId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addInterest(contactId, new ContactInterestRequest(propertyId, null)))
                .isInstanceOf(ContactInterestAlreadyExistsException.class);
    }

}
