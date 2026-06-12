package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.contact.api.dto.ContactImportReport;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.contact.domain.ProcessingBasis;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContactImportService} (CSV import) — Docker-free (pure Mockito,
 * real jakarta Validator so the import enforces the same bean constraints as the form).
 */
@ExtendWith(MockitoExtension.class)
class ContactImportServiceTest {

    @Mock private ContactService contactService;
    @Mock private ContactRepository contactRepository;
    @Mock private SocieteContextHelper societeCtx;

    private ContactImportService service;
    private final UUID societeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new ContactImportService(contactService, contactRepository, validator, societeCtx);
        when(societeCtx.requireSocieteId()).thenReturn(societeId);
        lenient().when(contactRepository.existsBySocieteIdAndEmail(eq(societeId), anyString())).thenReturn(false);
        lenient().when(contactRepository.existsBySocieteIdAndPhone(eq(societeId), anyString())).thenReturn(false);
    }

    private static byte[] csv(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Happy path: semicolon delimiter, accented French headers, quoted field")
    void happyPath_createsContacts() {
        ContactImportReport report = service.importCsv(csv("""
                Prénom;Nom;Téléphone;Email
                Karim;Alaoui;0612345678;karim@mail.ma
                "Salma";Bennani;;salma@mail.ma
                """), false, true, null);

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.errorCount()).isZero();
        assertThat(report.duplicateCount()).isZero();
        ArgumentCaptor<CreateContactRequest> captor = ArgumentCaptor.forClass(CreateContactRequest.class);
        verify(contactService, times(2)).create(captor.capture());
        assertThat(captor.getAllValues().get(0).firstName()).isEqualTo("Karim");
        assertThat(captor.getAllValues().get(0).consentGiven()).isTrue();
    }

    @Test
    @DisplayName("Row without phone AND email fails bean validation, is reported with its line")
    void phoneOrEmailMissing_reportedAsError() {
        ContactImportReport report = service.importCsv(csv("""
                prenom,nom,email
                Karim,Alaoui,karim@mail.ma
                Sans,Coordonnees,
                """), false, true, null);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.errorCount()).isEqualTo(1);
        assertThat(report.errors().get(0).line()).isEqualTo(3);
        verify(contactService, times(1)).create(any());
    }

    @Test
    @DisplayName("Duplicates: in-file phone collision and existing email in the société are skipped")
    void duplicates_skipped() {
        when(contactRepository.existsBySocieteIdAndEmail(societeId, "deja@mail.ma")).thenReturn(true);

        ContactImportReport report = service.importCsv(csv("""
                prenom,nom,telephone,email
                Karim,Alaoui,06 12 34 56 78,karim@mail.ma
                Karim,Doublon,0612345678,autre@mail.ma
                Salma,Bennani,0699999999,deja@mail.ma
                """), false, true, null);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.duplicateCount()).isEqualTo(2);
        assertThat(report.duplicates()).extracting(ContactImportReport.RowIssue::line).containsExactly(3, 4);
        verify(contactService, times(1)).create(any());
    }

    @Test
    @DisplayName("Dry run validates and dedups but never creates")
    void dryRun_neverPersists() {
        ContactImportReport report = service.importCsv(csv("""
                prenom,nom,telephone
                Karim,Alaoui,0612345678
                """), true, false, ProcessingBasis.LEGITIMATE_INTEREST);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.created()).isEqualTo(1);
        verify(contactService, never()).create(any());
    }

    @Test
    @DisplayName("Missing prénom/nom columns → IMPORT_VALIDATION_ERROR with actionable message")
    void missingRequiredColumns_rejected() {
        assertThatThrownBy(() -> service.importCsv(csv("email,telephone\na@b.ma,0600000000\n"),
                false, true, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prénom");
    }

    @Test
    @DisplayName("Loi 09-08: neither consent nor legal basis → CONSENT_REQUIRED before any parsing")
    void consentOrBasisRequired() {
        assertThatThrownBy(() -> service.importCsv(csv("prenom,nom\nA,B\n"), false, false, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONSENT_REQUIRED);
        verify(contactService, never()).create(any());
    }

    @Test
    @DisplayName("Per-row business failures (e.g. quota reached) are reported, import continues")
    void perRowBusinessFailure_reported() {
        when(contactService.create(any()))
                .thenThrow(new BusinessRuleException(ErrorCode.QUOTA_CONTACTS_ATTEINT, "Quota de contacts atteint"))
                .thenReturn(null);

        ContactImportReport report = service.importCsv(csv("""
                prenom,nom,telephone
                Karim,Alaoui,0612345678
                Salma,Bennani,0699999999
                """), false, true, null);

        assertThat(report.errorCount()).isEqualTo(1);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.errors().get(0).reason()).contains("Quota");
    }

    @Test
    @DisplayName("Unknown columns are ignored and reported, not treated as errors")
    void unknownColumnsIgnored() {
        ContactImportReport report = service.importCsv(csv("""
                prenom,nom,telephone,budget
                Karim,Alaoui,0612345678,900000
                """), true, true, null);

        assertThat(report.ignoredColumns()).containsExactly("budget");
        assertThat(report.created()).isEqualTo(1);
    }
}
