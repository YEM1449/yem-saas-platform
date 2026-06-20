package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.visite.api.dto.CompteRenduRequest;
import com.yem.hlm.backend.visite.api.dto.CreateVisiteRequest;
import com.yem.hlm.backend.visite.domain.*;
import com.yem.hlm.backend.visite.repo.VisiteRappelRepository;
import com.yem.hlm.backend.visite.repo.VisiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VisiteService} business rules — Docker-free (pure Mockito).
 *
 * Covers: invalid transition (RG-V02), slot conflict 409 (RG-V05), REALISEE without
 * compte-rendu refused (RG-V06), cancellation cancels pending reminders (RG-V07/V08),
 * AGENT isolation from another agent's visite (RG-V04).
 */
@ExtendWith(MockitoExtension.class)
class VisiteServiceTest {

    @Mock VisiteRepository visiteRepo;
    @Mock VisiteRappelRepository rappelRepo;
    @Mock ContactRepository contactRepo;
    @Mock UserRepository userRepo;
    @Mock AppUserSocieteRepository membershipRepo;
    @Mock PropertyRepository propertyRepo;
    @Mock ProjectRepository projectRepo;
    @Mock SocieteContextHelper societeCtx;
    @Mock VisiteEmailService emailService;

    VisiteService service;

    final UUID societeId = UUID.randomUUID();
    final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VisiteService(visiteRepo, rappelRepo, contactRepo, userRepo, membershipRepo,
                propertyRepo, projectRepo, societeCtx, emailService);
        lenient().when(societeCtx.requireSocieteId()).thenReturn(societeId);
        lenient().when(societeCtx.requireUserId()).thenReturn(actorId);
        lenient().when(societeCtx.getRole()).thenReturn("ROLE_MANAGER");
    }

    private User mockAgent(UUID id) {
        User agent = mock(User.class);
        lenient().when(agent.getId()).thenReturn(id);
        return agent;
    }

    private Visite visite(StatutVisite statut, User agent, Instant dateHeure) {
        Contact contact = mock(Contact.class);
        Visite v = new Visite(societeId, agent, contact, dateHeure, 30, TypeVisite.SUR_SITE, actorId);
        v.setStatut(statut);
        return v;
    }

    @Test
    @DisplayName("RG-V02 — confirmer sur visite REALISEE (terminale) → InvalidVisiteTransitionException")
    void transitionInvalide() {
        UUID id = UUID.randomUUID();
        Visite v = visite(StatutVisite.REALISEE, mockAgent(actorId), Instant.now());
        when(visiteRepo.findBySocieteIdAndId(societeId, id)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirmer(id))
                .isInstanceOf(InvalidVisiteTransitionException.class);
    }

    @Test
    @DisplayName("RG-V05 — créneau chevauchant pour le même agent → ConflitVisiteException (409)")
    void conflitDeCreneau() {
        Instant debut = Instant.now().plus(2, ChronoUnit.DAYS);
        CreateVisiteRequest req = new CreateVisiteRequest(
                UUID.randomUUID(), null, null, null, debut, 30, TypeVisite.SUR_SITE, null, false);

        User agent = mockAgent(actorId);
        when(userRepo.findById(actorId)).thenReturn(Optional.of(agent));
        when(contactRepo.findBySocieteIdAndId(societeId, req.contactId()))
                .thenReturn(Optional.of(mock(Contact.class)));

        Visite chevauchante = mock(Visite.class);
        when(chevauchante.getFin()).thenReturn(debut.plusSeconds(600)); // ends after debut → overlap
        when(visiteRepo.findConflitCandidats(eq(societeId), eq(actorId), any(), any(), isNull()))
                .thenReturn(List.of(chevauchante));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ConflitVisiteException.class);
        verify(visiteRepo, never()).save(any());
    }

    @Test
    @DisplayName("RG-V06 — REALISEE sans compte-rendu → CompteRenduRequisException (422)")
    void realiseeSansCompteRendu() {
        UUID id = UUID.randomUUID();
        Visite v = visite(StatutVisite.CONFIRMEE, mockAgent(actorId), Instant.now());
        when(visiteRepo.findBySocieteIdAndId(societeId, id)).thenReturn(Optional.of(v));

        CompteRenduRequest req = new CompteRenduRequest("   ", null);
        assertThatThrownBy(() -> service.enregistrerCompteRendu(id, req))
                .isInstanceOf(CompteRenduRequisException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVisite.CONFIRMEE); // not transitioned
    }

    @Test
    @DisplayName("RG-V08 — annulation passe les rappels EN_ATTENTE à ANNULE")
    void annulationAnnuleRappels() {
        UUID id = UUID.randomUUID();
        Visite v = visite(StatutVisite.CONFIRMEE, mockAgent(actorId), Instant.now().plus(2, ChronoUnit.DAYS));
        when(visiteRepo.findBySocieteIdAndId(societeId, id)).thenReturn(Optional.of(v));

        VisiteRappel r1 = new VisiteRappel(societeId, id, TypeRappel.H24, DestinataireRappel.AGENT, Instant.now());
        VisiteRappel r2 = new VisiteRappel(societeId, id, TypeRappel.H1, DestinataireRappel.AGENT, Instant.now());
        when(rappelRepo.findByVisiteIdAndStatut(any(), eq(StatutRappel.EN_ATTENTE)))
                .thenReturn(List.of(r1, r2));

        service.annuler(id, "Client indisponible");

        assertThat(v.getStatut()).isEqualTo(StatutVisite.ANNULEE);
        assertThat(v.getAnnulationRaison()).isEqualTo("Client indisponible");
        assertThat(r1.getStatut()).isEqualTo(StatutRappel.ANNULE);
        assertThat(r2.getStatut()).isEqualTo(StatutRappel.ANNULE);
        verify(rappelRepo).saveAll(any());
    }

    @Test
    @DisplayName("RG-V04 — un AGENT ne voit pas la visite d'un autre agent → VisiteNotFoundException")
    void isolationAgent() {
        when(societeCtx.getRole()).thenReturn("ROLE_AGENT");
        UUID id = UUID.randomUUID();
        UUID autreAgent = UUID.randomUUID();
        Visite v = visite(StatutVisite.PLANIFIEE, mockAgent(autreAgent), Instant.now());
        when(visiteRepo.findBySocieteIdAndId(societeId, id)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(VisiteNotFoundException.class);
    }
}
