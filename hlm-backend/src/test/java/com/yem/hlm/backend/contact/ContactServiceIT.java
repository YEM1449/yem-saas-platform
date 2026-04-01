package com.yem.hlm.backend.contact;

import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.service.*;
import com.yem.hlm.backend.deposit.service.DepositAlreadyExistsException;
import com.yem.hlm.backend.deposit.service.InvalidDepositRequestException;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ContactServiceIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ContactService contactService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SocieteRepository societeRepository;

    @BeforeEach
    void setUpSocieteContext() {
        SocieteContext.setSocieteId(TENANT_ID);
        SocieteContext.setUserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    }

    @AfterEach
    void clearSocieteContext() {
        SocieteContext.clear();
    }

    @Test
    void createContact_defaultsToProspect() {
        ContactResponse created = contactService.create(new CreateContactRequest(
                "example ana",
                "youssouf",
                "0612345678",
                "ana.youssouf@example.com",
                null,
                null,
                null,
                true,
                null,
                null
        ));

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ContactStatus.PROSPECT);
        assertThat(created.firstName()).isEqualTo("example ana");
        assertThat(created.lastName()).isEqualTo("youssouf");
    }

    @Test
    void convertToClient_requiresPropertyAndAmount() {
        ContactResponse created = contactService.create(new CreateContactRequest(
                "Hamza",
                "patron",
                null,
                "hamza.patron@example.com",
                null,
                null,
                null,
                true,
                null,
                null
        ));

        assertThatThrownBy(() -> contactService.convertToClient(created.id(), new ConvertToClientRequest(
                null,
                new BigDecimal("1000.00"),
                LocalDate.now(),
                null,
                "MAD",
                LocalDateTime.now().plusDays(7)
        ))).isInstanceOf(InvalidDepositRequestException.class);

        assertThatThrownBy(() -> contactService.convertToClient(created.id(), new ConvertToClientRequest(
                UUID.randomUUID(),
                null,
                LocalDate.now(),
                null,
                "MAD",
                LocalDateTime.now().plusDays(7)
        ))).isInstanceOf(InvalidDepositRequestException.class);
    }

    @Test
    void convertToClient_success_createsReservation_andSetsTempClientWorkflow() {
        ContactResponse created = contactService.create(new CreateContactRequest(
                "Karim",
                "aassila",
                null,
                "aassila@example.com",
                null,
                null,
                null,
                true,
                null,
                null
        ));

        UUID propertyId = createActiveProperty();

        ContactResponse converted = contactService.convertToClient(created.id(), new ConvertToClientRequest(
                propertyId,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 2, 1),
                null,
                "MAD",
                LocalDateTime.now().plusDays(7)
        ));

        assertThat(converted.qualified()).isTrue();
        assertThat(converted.status()).isEqualTo(ContactStatus.QUALIFIED_PROSPECT);
        assertThat(converted.contactType().name()).isIn("TEMP_CLIENT", "CLIENT");
        assertThat(converted.tempClientUntil()).isNotNull();
    }

    @Test
    void convertToClient_twice_sameProperty_throwsConflict() {
        ContactResponse created = contactService.create(new CreateContactRequest(
                "dounia",
                "Z",
                null,
                "dounia@example.com",
                null,
                null,
                null,
                true,
                null,
                null
        ));

        UUID propertyId = createActiveProperty();

        contactService.convertToClient(created.id(), new ConvertToClientRequest(
                propertyId,
                new BigDecimal("100.00"),
                LocalDate.now(),
                null,
                "MAD",
                LocalDateTime.now().plusDays(7)
        ));

        assertThatThrownBy(() -> contactService.convertToClient(created.id(), new ConvertToClientRequest(
                propertyId,
                new BigDecimal("200.00"),
                LocalDate.now(),
                null,
                "MAD",
                LocalDateTime.now().plusDays(7)
        ))).isInstanceOf(DepositAlreadyExistsException.class);
    }

    @Test
    void listContacts_filtersByQueryAndStatus() {
        contactService.create(new CreateContactRequest("Abou Hamza", "Louz", null, "john1@example.com", null, null, null, true, null, null));
        contactService.create(new CreateContactRequest("Boubker", "Hamzaoui", null, "bob@example.com", null, null, null, true, null, null));
        contactService.create(new CreateContactRequest("hamza", "Igaman", null, "john2@example.com", null, null, null, true, null, null));

        var page = contactService.list(null, ContactStatus.PROSPECT, "hamza", PageRequest.of(0, 10));  // null = no type filter
        assertThat(page.getTotalElements()).isEqualTo(3); // Abou Hamza Louz, Boubker Hamzaoui, hamza Igaman
    }

    // ── helpers ──

    private int refCounter = 0;

    private UUID createActiveProperty() {
        int ref = ++refCounter;
        Societe societe = societeRepository.getReferenceById(TENANT_ID);
        Project project = projectRepository.saveAndFlush(new Project(societe.getId(), "CSI-Project-" + ref));
        Property property = new Property(societe.getId(), project, PropertyType.VILLA, SocieteContext.getUserId());
        property.setReferenceCode("CSI-" + ref);
        property.setTitle("Test Property " + ref);
        property.setPrice(new BigDecimal("500000"));
        property.setStatus(PropertyStatus.ACTIVE);
        return propertyRepository.saveAndFlush(property).getId();
    }
}
