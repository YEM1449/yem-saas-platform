package com.yem.hlm.backend.groupe.service;

import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.ContactRef;
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
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Group-level client identity (finding #005) — "link, don't merge".
 *
 * <p>Lets a group owner recognise that a buyer in société A is the same physical person as a
 * buyer in société B (the highest-margin repeat-buyer case) <em>without</em> merging the two
 * contact records: each société keeps its own row, and the cross-société link is only created
 * with explicit consent (Loi 09-08 requires a legal basis for intra-group transfer of personal
 * data — Nadia's caution).
 *
 * <p><b>Isolation.</b> Like {@code GroupDashboardService}, this never widens a query across
 * sociétés. It resolves the caller's ADMIN memberships, then reads each société's contacts one
 * at a time by switching {@code SocieteContext} so RLS scopes the read; the link table itself is
 * cross-société infrastructure (no RLS) and stores only opaque IDs, never PII.
 */
@Service
public class GroupClientService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final ClientGroupeLienRepository linkRepository;
    private final ContactRepository contactRepository;
    private final AppUserSocieteRepository membershipRepository;
    private final SocieteRepository societeRepository;
    private final SocieteContextHelper societeCtx;

    public GroupClientService(ClientGroupeLienRepository linkRepository,
                              ContactRepository contactRepository,
                              AppUserSocieteRepository membershipRepository,
                              SocieteRepository societeRepository,
                              SocieteContextHelper societeCtx) {
        this.linkRepository = linkRepository;
        this.contactRepository = contactRepository;
        this.membershipRepository = membershipRepository;
        this.societeRepository = societeRepository;
        this.societeCtx = societeCtx;
    }

    // ── Candidates ────────────────────────────────────────────────────────────

    /**
     * Same-CIN contacts found in ≥2 of the owner's ADMIN sociétés that are not already in one
     * cluster together. These are the repeat-buyer candidates the owner can choose to link.
     */
    @Transactional(readOnly = true)
    public List<LinkCandidate> findCandidates() {
        List<UUID> adminSocietes = adminSocieteIds();
        Map<UUID, String> societeNames = societeNames(adminSocietes);

        // normalizedCin → contacts carrying it across the owner's sociétés
        Map<String, List<ContactRef>> byCin = new LinkedHashMap<>();
        runPerSociete(adminSocietes, societeId -> {
            for (Contact c : contactRepository.findWithNationalId(societeId)) {
                String key = normalizeCin(c.getNationalId());
                if (key == null) continue;
                byCin.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(toRef(c, societeId, societeNames.get(societeId)));
            }
        });

        List<LinkCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ContactRef>> e : byCin.entrySet()) {
            List<ContactRef> refs = e.getValue();
            long distinctSocietes = refs.stream().map(ContactRef::societeId).distinct().count();
            if (distinctSocietes < 2) continue;                 // same person must span ≥2 sociétés
            if (refs.stream().allMatch(ContactRef::dejaLie) && sameCluster(refs)) continue; // nothing to add
            candidates.add(new LinkCandidate(maskCin(e.getKey()), refs));
        }
        return candidates;
    }

    // ── Linking ───────────────────────────────────────────────────────────────

    /**
     * Links the given contacts as one group person. Requires explicit consent (Loi 09-08),
     * that the caller is ADMIN of every involved société, and that all contacts share the same
     * CIN (defensive — the UI proposes matched sets, but the API verifies).
     */
    @Transactional
    public GroupClient link(LinkClientsRequest request) {
        if (!request.consentGiven()) {
            throw new BusinessRuleException(ErrorCode.CONSENT_REQUIRED,
                    "Le consentement du client est requis pour lier ses identités entre sociétés (Loi 09-08).");
        }
        UUID actorId = societeCtx.requireUserId();
        List<UUID> adminSocietes = adminSocieteIds();

        // Resolve each contact within the owner's ADMIN sociétés (enforces authority + existence).
        List<ResolvedContact> resolved = new ArrayList<>();
        for (UUID contactId : request.contactIds().stream().distinct().toList()) {
            resolved.add(resolveContact(contactId, adminSocietes));
        }

        // All must be the same person.
        long distinctCins = resolved.stream().map(r -> normalizeCin(r.contact.getNationalId())).distinct().count();
        if (distinctCins != 1 || normalizeCin(resolved.get(0).contact.getNationalId()) == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Les contacts sélectionnés doivent avoir le même numéro de CIN.");
        }

        // Reuse an existing cluster if any contact is already linked, else start a new one.
        UUID clusterId = resolved.stream()
                .map(r -> linkRepository.findByContactId(r.contact.getId()))
                .filter(Optional::isPresent).map(Optional::get)
                .map(ClientGroupeLien::getGroupePersonneId)
                .findFirst()
                .orElse(UUID.randomUUID());

        for (ResolvedContact r : resolved) {
            Optional<ClientGroupeLien> existing = linkRepository.findByContactId(r.contact.getId());
            if (existing.isPresent()) {
                ClientGroupeLien link = existing.get();
                link.setGroupePersonneId(clusterId);
                link.setConsentGiven(true);
                linkRepository.save(link);
            } else {
                linkRepository.save(new ClientGroupeLien(
                        clusterId, r.contact.getId(), r.societeId, true, actorId));
            }
        }
        return buildGroupClient(clusterId, adminSocietes, societeNames(adminSocietes));
    }

    /**
     * Removes a contact from its group cluster (consent withdrawal — Loi 09-08 right). If only
     * one contact would remain, the whole cluster is dissolved (a cluster of one is meaningless).
     */
    @Transactional
    public void unlink(UUID contactId) {
        List<UUID> adminSocietes = adminSocieteIds();
        ClientGroupeLien link = linkRepository.findByContactId(contactId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                        "Ce contact n'est pas lié à une identité groupe."));
        if (!adminSocietes.contains(link.getSocieteId())) {
            throw new AccessDeniedException("Vous n'administrez pas la société de ce contact.");
        }
        List<ClientGroupeLien> cluster = linkRepository.findByGroupePersonneId(link.getGroupePersonneId());
        linkRepository.delete(link);
        if (cluster.size() - 1 <= 1) {
            cluster.stream().filter(l -> !l.getId().equals(link.getId())).forEach(linkRepository::delete);
        }
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /** All established group-person clusters that touch the owner's ADMIN sociétés. */
    @Transactional(readOnly = true)
    public List<GroupClient> listGroupClients() {
        List<UUID> adminSocietes = adminSocieteIds();
        Map<UUID, String> names = societeNames(adminSocietes);
        List<ClientGroupeLien> mine = linkRepository.findBySocieteIdIn(adminSocietes);

        List<UUID> clusterIds = mine.stream().map(ClientGroupeLien::getGroupePersonneId).distinct().toList();
        List<GroupClient> result = new ArrayList<>();
        for (UUID clusterId : clusterIds) {
            result.add(buildGroupClient(clusterId, adminSocietes, names));
        }
        result.sort(Comparator.comparing(GroupClient::lieLe).reversed());
        return result;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private GroupClient buildGroupClient(UUID clusterId, List<UUID> adminSocietes, Map<UUID, String> names) {
        List<ClientGroupeLien> links = linkRepository.findByGroupePersonneId(clusterId);
        List<ContactRef> refs = new ArrayList<>();
        boolean consent = !links.isEmpty();
        Instant lieLe = Instant.now();
        for (ClientGroupeLien link : links) {
            consent &= link.isConsentGiven();
            if (link.getCreatedAt().isBefore(lieLe)) lieLe = link.getCreatedAt();
            // Only render contacts in sociétés the caller administers (others stay hidden).
            if (!adminSocietes.contains(link.getSocieteId())) continue;
            Contact c = loadContactInSociete(link.getContactId(), link.getSocieteId());
            if (c != null) {
                refs.add(toRef(c, link.getSocieteId(), names.get(link.getSocieteId())));
            }
        }
        return new GroupClient(clusterId, consent, lieLe, refs);
    }

    private record ResolvedContact(Contact contact, UUID societeId) {}

    /** Finds a contact across the owner's ADMIN sociétés; 404/403 if not reachable. */
    private ResolvedContact resolveContact(UUID contactId, List<UUID> adminSocietes) {
        for (UUID societeId : adminSocietes) {
            Contact c = loadContactInSociete(contactId, societeId);
            if (c != null) return new ResolvedContact(c, societeId);
        }
        throw new AccessDeniedException(
                "Contact introuvable dans vos sociétés ou hors de votre périmètre d'administration : " + contactId);
    }

    /** Reads one contact with {@code SocieteContext} pointed at its société (RLS scope). */
    private Contact loadContactInSociete(UUID contactId, UUID societeId) {
        UUID original = SocieteContext.getSocieteId();
        try {
            SocieteContext.setSocieteId(societeId);
            return contactRepository.findBySocieteIdAndId(societeId, contactId).orElse(null);
        } finally {
            SocieteContext.setSocieteId(original);
        }
    }

    /** Runs an action once per société with the context switched, restoring it afterwards. */
    private void runPerSociete(List<UUID> societeIds, java.util.function.Consumer<UUID> action) {
        UUID original = SocieteContext.getSocieteId();
        try {
            for (UUID societeId : societeIds) {
                SocieteContext.setSocieteId(societeId);
                action.accept(societeId);
            }
        } finally {
            SocieteContext.setSocieteId(original);
        }
    }

    private List<UUID> adminSocieteIds() {
        UUID userId = societeCtx.requireUserId();
        List<UUID> ids = membershipRepository.findByIdUserIdAndActifTrue(userId).stream()
                .filter(m -> ADMIN_ROLE.equals(m.getRole()))
                .map(AppUserSociete::getSocieteId)
                .toList();
        if (ids.isEmpty()) {
            throw new AccessDeniedException("Fonction réservée aux administrateurs de société.");
        }
        return ids;
    }

    private Map<UUID, String> societeNames(List<UUID> societeIds) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : societeIds) {
            societeRepository.findById(id).ifPresent(s -> names.put(id, s.getNom()));
        }
        return names;
    }

    private ContactRef toRef(Contact c, UUID societeId, String societeNom) {
        return new ContactRef(
                c.getId(), societeId, societeNom, c.getFullName(),
                c.getStatus() != null ? c.getStatus().name() : null,
                linkRepository.existsByContactId(c.getId()));
    }

    private boolean sameCluster(List<ContactRef> refs) {
        List<UUID> clusters = refs.stream()
                .map(r -> linkRepository.findByContactId(r.contactId()).map(ClientGroupeLien::getGroupePersonneId).orElse(null))
                .distinct().toList();
        return clusters.size() == 1 && clusters.get(0) != null;
    }

    /** Upper-cased, stripped of spaces/dashes; null when blank. */
    static String normalizeCin(String cin) {
        if (cin == null) return null;
        String n = cin.replaceAll("[\\s-]", "").toUpperCase();
        return n.isBlank() ? null : n;
    }

    /** Masks all but the last 4 characters: "AB123456" → "••••3456". */
    static String maskCin(String normalizedCin) {
        if (normalizedCin == null) return null;
        int keep = Math.min(4, normalizedCin.length());
        return "••••" + normalizedCin.substring(normalizedCin.length() - keep);
    }
}
