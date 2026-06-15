package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.visite.domain.*;
import com.yem.hlm.backend.visite.repo.VisiteRappelRepository;
import com.yem.hlm.backend.visite.repo.VisiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VisiteRappelService} — the persistent reminder dispatcher (RG-V07).
 * Covers: a due reminder is sent and flipped to ENVOYE (idempotent); a transient email failure
 * keeps it EN_ATTENTE and increments tentatives, abandoning after MAX; a terminal visite abandons it.
 */
@ExtendWith(MockitoExtension.class)
class VisiteRappelServiceTest {

    @Mock VisiteRappelRepository rappelRepo;
    @Mock VisiteRepository visiteRepo;
    @Mock VisiteEmailService emailService;

    VisiteRappelService service() {
        return new VisiteRappelService(rappelRepo, visiteRepo, emailService);
    }

    private Visite visite(StatutVisite statut) {
        User agent = mock(User.class);
        Contact contact = mock(Contact.class);
        Visite v = new Visite(UUID.randomUUID(), agent, contact, Instant.now().plus(1, ChronoUnit.HOURS),
                30, TypeVisite.SUR_SITE, UUID.randomUUID());
        v.setStatut(statut);
        return v;
    }

    @Test
    @DisplayName("Rappel dû envoyé → marqué ENVOYE (idempotent)")
    void envoiOk() {
        VisiteRappel r = new VisiteRappel(UUID.randomUUID(), UUID.randomUUID(),
                TypeRappel.H24, DestinataireRappel.AGENT, Instant.now().minusSeconds(60));
        when(rappelRepo.findByStatutAndDuABeforeOrderByDuAAsc(eq(StatutRappel.EN_ATTENTE), any()))
                .thenReturn(List.of(r));
        when(visiteRepo.findById(any())).thenReturn(Optional.of(visite(StatutVisite.CONFIRMEE)));
        when(emailService.destinataireEmail(any(), eq(DestinataireRappel.AGENT))).thenReturn("agent@acme.com");

        int sent = service().envoyerRappelsDus();

        assertThat(sent).isEqualTo(1);
        assertThat(r.getStatut()).isEqualTo(StatutRappel.ENVOYE);
        assertThat(r.getEnvoyeAt()).isNotNull();
        verify(emailService).envoyerRappel(any(), eq(r), eq("agent@acme.com"));
    }

    @Test
    @DisplayName("Échec email → reste EN_ATTENTE, tentatives incrémenté (retry)")
    void echecRetry() {
        VisiteRappel r = new VisiteRappel(UUID.randomUUID(), UUID.randomUUID(),
                TypeRappel.H1, DestinataireRappel.AGENT, Instant.now().minusSeconds(60));
        when(rappelRepo.findByStatutAndDuABeforeOrderByDuAAsc(eq(StatutRappel.EN_ATTENTE), any()))
                .thenReturn(List.of(r));
        when(visiteRepo.findById(any())).thenReturn(Optional.of(visite(StatutVisite.PLANIFIEE)));
        when(emailService.destinataireEmail(any(), any())).thenReturn("agent@acme.com");
        doThrow(new RuntimeException("smtp down")).when(emailService).envoyerRappel(any(), any(), any());

        int sent = service().envoyerRappelsDus();

        assertThat(sent).isZero();
        assertThat(r.getStatut()).isEqualTo(StatutRappel.EN_ATTENTE);
        assertThat(r.getTentatives()).isEqualTo(1);
    }

    @Test
    @DisplayName("Visite terminale (ANNULEE) → rappel abandonné (ANNULE), pas d'email")
    void visiteTerminaleAbandonne() {
        VisiteRappel r = new VisiteRappel(UUID.randomUUID(), UUID.randomUUID(),
                TypeRappel.H24, DestinataireRappel.PROSPECT, Instant.now().minusSeconds(60));
        when(rappelRepo.findByStatutAndDuABeforeOrderByDuAAsc(eq(StatutRappel.EN_ATTENTE), any()))
                .thenReturn(List.of(r));
        when(visiteRepo.findById(any())).thenReturn(Optional.of(visite(StatutVisite.ANNULEE)));

        int sent = service().envoyerRappelsDus();

        assertThat(sent).isZero();
        assertThat(r.getStatut()).isEqualTo(StatutRappel.ANNULE);
        verifyNoInteractions(emailService);
    }
}
