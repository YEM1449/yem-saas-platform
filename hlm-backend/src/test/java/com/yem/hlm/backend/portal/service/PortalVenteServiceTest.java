package com.yem.hlm.backend.portal.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.vente.domain.Vente;
import com.yem.hlm.backend.vente.domain.VenteDocument;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.vente.service.VenteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PortalVenteService} access control — Docker-free (pure Mockito).
 *
 * <p>Focus: DA-006 — a buyer must not be able to download a document that does not belong to the
 * vente they own. The lookup is scoped to {@code (societeId, venteId, docId)}, so a {@code docId}
 * that exists elsewhere in the société is unreachable and yields 404 with no information leak.
 */
@ExtendWith(MockitoExtension.class)
class PortalVenteServiceTest {

    @Mock private VenteRepository         venteRepository;
    @Mock private VenteDocumentRepository documentRepository;
    @Mock private VenteService            venteService;
    @Mock private MediaStorageService     mediaStorage;

    private PortalVenteService service;

    private final UUID societeId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID venteId   = UUID.randomUUID();

    @Mock private Vente   ownedVente;
    @Mock private Contact buyer;

    @BeforeEach
    void setUp() {
        service = new PortalVenteService(venteRepository, documentRepository, venteService, mediaStorage);

        // Portal request context: société from JWT "sid", contactId as the authenticated principal.
        SocieteContext.setSocieteId(societeId);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(contactId, null));

        // The caller legitimately owns venteId.
        lenient().when(venteRepository.findBySocieteIdAndId(societeId, venteId))
                .thenReturn(Optional.of(ownedVente));
        lenient().when(ownedVente.getContact()).thenReturn(buyer);
        lenient().when(buyer.getId()).thenReturn(contactId);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getDocumentKey returns the storage key for a document attached to the buyer's own vente")
    void returnsKeyForOwnedVenteDocument() {
        UUID docId = UUID.randomUUID();
        VenteDocument doc = org.mockito.Mockito.mock(VenteDocument.class);
        when(doc.getStorageKey()).thenReturn("ventes/" + venteId + "/contrat.pdf");
        when(documentRepository.findBySocieteIdAndVente_IdAndId(societeId, venteId, docId))
                .thenReturn(Optional.of(doc));

        assertThat(service.getDocumentKey(venteId, docId))
                .isEqualTo("ventes/" + venteId + "/contrat.pdf");
    }

    @Test
    @DisplayName("DA-006: a docId not attached to the owned vente is unreachable → 404 (no info leak)")
    void rejectsForeignDocumentEvenWhenItExistsInTheSociete() {
        // The attacker owns venteId and supplies another buyer's document id. The vente-scoped query
        // returns empty (the doc belongs to a different vente), so the IDOR cannot succeed.
        UUID foreignDocId = UUID.randomUUID();
        when(documentRepository.findBySocieteIdAndVente_IdAndId(societeId, venteId, foreignDocId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocumentKey(venteId, foreignDocId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        // The vulnerable, société-only lookup must never be used as a fallback.
        verify(documentRepository, never()).findBySocieteIdAndId(societeId, foreignDocId);
    }

    @Test
    @DisplayName("getDocumentKey on a vente owned by another contact → 404 before any document lookup")
    void rejectsDocumentAccessOnVenteOwnedByAnotherContact() {
        UUID someoneElse = UUID.randomUUID();
        when(buyer.getId()).thenReturn(someoneElse); // the vente belongs to a different buyer

        assertThatThrownBy(() -> service.getDocumentKey(venteId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(documentRepository, never())
                .findBySocieteIdAndVente_IdAndId(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
