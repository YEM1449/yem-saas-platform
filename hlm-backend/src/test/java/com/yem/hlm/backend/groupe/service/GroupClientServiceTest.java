package com.yem.hlm.backend.groupe.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.GroupClient;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.LinkCandidate;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.LinkClientsRequest;
import com.yem.hlm.backend.groupe.domain.ClientGroupeLien;
import com.yem.hlm.backend.groupe.repo.ClientGroupeLienRepository;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupClientServiceTest {

    @Mock private ClientGroupeLienRepository linkRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private AppUserSocieteRepository membershipRepository;
    @Mock private SocieteRepository societeRepository;
    @Mock private SocieteContextHelper societeCtx;

    private GroupClientService service;

    private final UUID userId   = UUID.randomUUID();
    private final UUID atlas    = UUID.randomUUID();
    private final UUID riad     = UUID.randomUUID();
    private final List<ClientGroupeLien> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new GroupClientService(linkRepository, contactRepository,
                membershipRepository, societeRepository, societeCtx);
        SocieteContext.setSocieteId(atlas);
        lenient().when(societeCtx.requireUserId()).thenReturn(userId);

        // Owner is ADMIN of both Atlas and Riad by default.
        lenient().when(membershipRepository.findByIdUserIdAndActifTrue(userId)).thenReturn(List.of(
                new AppUserSociete(new AppUserSocieteId(userId, atlas), "ADMIN"),
                new AppUserSociete(new AppUserSocieteId(userId, riad), "ADMIN")));
        // Precompute society mocks before stubbing (nested mock() inside when() is illegal).
        Societe atlasSoc = societe(atlas, "Atlas");
        Societe riadSoc = societe(riad, "Riad");
        lenient().when(societeRepository.findById(atlas)).thenReturn(Optional.of(atlasSoc));
        lenient().when(societeRepository.findById(riad)).thenReturn(Optional.of(riadSoc));

        // In-memory fake for the link table.
        lenient().when(linkRepository.save(any())).thenAnswer(inv -> {
            ClientGroupeLien l = inv.getArgument(0);
            store.removeIf(x -> x.getId().equals(l.getId()));
            store.add(l);
            return l;
        });
        lenient().when(linkRepository.findByContactId(any())).thenAnswer(inv ->
                store.stream().filter(l -> l.getContactId().equals(inv.getArgument(0))).findFirst());
        lenient().when(linkRepository.existsByContactId(any())).thenAnswer(inv ->
                store.stream().anyMatch(l -> l.getContactId().equals(inv.getArgument(0))));
        lenient().when(linkRepository.findByGroupePersonneId(any())).thenAnswer(inv ->
                store.stream().filter(l -> l.getGroupePersonneId().equals(inv.getArgument(0))).toList());
        lenient().when(linkRepository.findBySocieteIdIn(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return store.stream().filter(l -> ids.contains(l.getSocieteId())).toList();
        });
        lenient().doAnswer(inv -> {
            ClientGroupeLien l = inv.getArgument(0);
            store.removeIf(x -> x.getId().equals(l.getId()));
            return null;
        }).when(linkRepository).delete(any());
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
        store.clear();
    }

    private Societe societe(UUID id, String nom) {
        Societe s = mock(Societe.class);
        lenient().when(s.getNom()).thenReturn(nom);
        return s;
    }

    private Contact contact(UUID id, String cin, String name) {
        Contact c = mock(Contact.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getNationalId()).thenReturn(cin);
        lenient().when(c.getFullName()).thenReturn(name);
        lenient().when(c.getStatus()).thenReturn(ContactStatus.ACTIVE_CLIENT);
        return c;
    }

    /** Registers a contact so it's found both by the per-société scan and by id lookup. */
    private void register(UUID societeId, Contact c) {
        lenient().when(contactRepository.findBySocieteIdAndId(societeId, c.getId())).thenReturn(Optional.of(c));
    }

    private void scan(UUID societeId, Contact... contacts) {
        lenient().when(contactRepository.findWithNationalId(societeId)).thenReturn(List.of(contacts));
    }

    @Test
    @DisplayName("Candidates: same CIN in two sociétés is surfaced with a masked CIN")
    void candidates_sameCinAcrossSocietes() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim Alaoui");
        Contact b = contact(UUID.randomUUID(), "ab 12 34 56", "Karim Alaoui");
        scan(atlas, a);
        scan(riad, b);

        List<LinkCandidate> candidates = service.findCandidates();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).cinMasque()).isEqualTo("••••3456");
        assertThat(candidates.get(0).contacts()).hasSize(2);
    }

    @Test
    @DisplayName("Candidates: a CIN in only one société is not a candidate")
    void candidates_singleSocieteIgnored() {
        scan(atlas, contact(UUID.randomUUID(), "AB123456", "Karim"));
        scan(riad);

        assertThat(service.findCandidates()).isEmpty();
    }

    @Test
    @DisplayName("Link without consent is rejected (Loi 09-08)")
    void link_requiresConsent() {
        UUID c1 = UUID.randomUUID(), c2 = UUID.randomUUID();
        assertThatThrownBy(() -> service.link(new LinkClientsRequest(List.of(c1, c2), false)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").hasToString("CONSENT_REQUIRED");
    }

    @Test
    @DisplayName("Link creates a cluster, persists one row per contact, returns the group client")
    void link_happyPath() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim Alaoui");
        Contact b = contact(UUID.randomUUID(), "AB123456", "Karim Alaoui");
        register(atlas, a);
        register(riad, b);

        GroupClient gc = service.link(new LinkClientsRequest(List.of(a.getId(), b.getId()), true));

        assertThat(store).hasSize(2);
        assertThat(store).allMatch(ClientGroupeLien::isConsentGiven);
        assertThat(store.stream().map(ClientGroupeLien::getGroupePersonneId).distinct()).hasSize(1);
        assertThat(gc.consentGiven()).isTrue();
        assertThat(gc.contacts()).hasSize(2);
    }

    @Test
    @DisplayName("Link rejects contacts with different CINs")
    void link_differentCinRejected() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim");
        Contact b = contact(UUID.randomUUID(), "ZZ999999", "Autre");
        register(atlas, a);
        register(riad, b);

        assertThatThrownBy(() -> service.link(new LinkClientsRequest(List.of(a.getId(), b.getId()), true)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").hasToString("VALIDATION_ERROR");
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("Link rejects a contact outside the owner's ADMIN sociétés")
    void link_contactOutsidePerimeter() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim");
        register(atlas, a);
        UUID strangerContact = UUID.randomUUID(); // not registered in any admin société

        assertThatThrownBy(() -> service.link(
                new LinkClientsRequest(List.of(a.getId(), strangerContact), true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Link reuses an existing cluster when one contact is already linked")
    void link_reusesExistingCluster() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim");
        Contact b = contact(UUID.randomUUID(), "AB123456", "Karim");
        Contact c = contact(UUID.randomUUID(), "AB123456", "Karim");
        register(atlas, a);
        register(riad, b);
        register(atlas, c);
        UUID existingCluster = UUID.randomUUID();
        store.add(new ClientGroupeLien(existingCluster, a.getId(), atlas, true, userId));

        service.link(new LinkClientsRequest(List.of(b.getId(), c.getId(), a.getId()), true));

        assertThat(store.stream().map(ClientGroupeLien::getGroupePersonneId).distinct())
                .containsExactly(existingCluster);
        assertThat(store).hasSize(3);
    }

    @Test
    @DisplayName("No ADMIN membership → 403 on any operation")
    void noAdminMembership_forbidden() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId)).thenReturn(List.of(
                new AppUserSociete(new AppUserSocieteId(userId, atlas), "MANAGER")));

        assertThatThrownBy(() -> service.findCandidates()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Unlink dissolves a 2-contact cluster entirely")
    void unlink_dissolvesPairCluster() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim");
        Contact b = contact(UUID.randomUUID(), "AB123456", "Karim");
        UUID cluster = UUID.randomUUID();
        store.add(new ClientGroupeLien(cluster, a.getId(), atlas, true, userId));
        store.add(new ClientGroupeLien(cluster, b.getId(), riad, true, userId));

        service.unlink(a.getId());

        assertThat(store).isEmpty(); // the lone remaining contact is unlinked too
    }

    @Test
    @DisplayName("Listing returns established clusters scoped to the owner's sociétés")
    void list_returnsClusters() {
        Contact a = contact(UUID.randomUUID(), "AB123456", "Karim");
        Contact b = contact(UUID.randomUUID(), "AB123456", "Karim");
        register(atlas, a);
        register(riad, b);
        UUID cluster = UUID.randomUUID();
        store.add(new ClientGroupeLien(cluster, a.getId(), atlas, true, userId));
        store.add(new ClientGroupeLien(cluster, b.getId(), riad, true, userId));

        List<GroupClient> clients = service.listGroupClients();

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).contacts()).hasSize(2);
        assertThat(clients.get(0).consentGiven()).isTrue();
    }
}
