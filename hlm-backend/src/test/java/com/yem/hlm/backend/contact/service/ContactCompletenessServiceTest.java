package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContactCompletenessService} — stage-based contact field validation
 * (RG-F1). Docker-free (pure Mockito).
 */
@ExtendWith(MockitoExtension.class)
class ContactCompletenessServiceTest {

    private static final UUID SOC = UUID.randomUUID();
    private static final UUID CONTACT = UUID.randomUUID();

    @Mock ContactRepository contactRepository;

    private ContactCompletenessService service() {
        return new ContactCompletenessService(contactRepository);
    }

    private Contact contact(String phone, String nationalId, String address) {
        Contact c = mock(Contact.class);
        lenient().when(c.getPhone()).thenReturn(phone);
        lenient().when(c.getNationalId()).thenReturn(nationalId);
        lenient().when(c.getAddress()).thenReturn(address);
        return c;
    }

    private void stub(Contact c) {
        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT)).thenReturn(Optional.of(c));
    }

    @Test
    @DisplayName("Unknown contact → ContactNotFoundException")
    void unknownContact_throws() {
        when(contactRepository.findBySocieteIdAndId(SOC, CONTACT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().validateForStage(SOC, CONTACT, ContactValidationStage.RESERVATION))
                .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("RESERVATION: phone present → passes")
    void reservation_withPhone_passes() {
        stub(contact("0600000000", null, null));
        assertThatCode(() -> service().validateForStage(SOC, CONTACT, ContactValidationStage.RESERVATION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RESERVATION: blank phone → ClientIncomplete listing phone")
    void reservation_blankPhone_throws() {
        stub(contact("  ", null, null));
        assertThatThrownBy(() -> service().validateForStage(SOC, CONTACT, ContactValidationStage.RESERVATION))
                .isInstanceOf(ClientIncompleteException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((ClientIncompleteException) ex).getMissingFields()).contains("phone"));
    }

    @Test
    @DisplayName("VENTE: phone + nationalId + address present → passes")
    void vente_complete_passes() {
        stub(contact("0600000000", "AB12345", "12 rue de Casablanca"));
        assertThatCode(() -> service().validateForStage(SOC, CONTACT, ContactValidationStage.VENTE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VENTE: missing nationalId + address → ClientIncomplete listing both")
    void vente_missingLegalFields_throws() {
        stub(contact("0600000000", null, null));
        assertThatThrownBy(() -> service().validateForStage(SOC, CONTACT, ContactValidationStage.VENTE))
                .isInstanceOf(ClientIncompleteException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((ClientIncompleteException) ex).getMissingFields())
                        .contains("nationalId", "address"));
    }
}
