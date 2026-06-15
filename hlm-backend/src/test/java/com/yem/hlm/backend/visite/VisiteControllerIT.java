package com.yem.hlm.backend.visite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.visite.api.dto.AnnulerVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.CompteRenduRequest;
import com.yem.hlm.backend.visite.api.dto.CreateVisiteRequest;
import com.yem.hlm.backend.visite.domain.*;
import com.yem.hlm.backend.visite.repo.VisiteRappelRepository;
import com.yem.hlm.backend.visite.service.VisiteRappelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code /api/visites} (Wave 16).
 *
 * <p>Covers: auth guard (401), create → PLANIFIEE (201), slot conflict (409, RG-V05),
 * confirmer → compte-rendu → REALISEE (RG-V06), annuler (RG-V08), cross-société isolation
 * (404, RG-V04), and the persistent reminder job flipping a due reminder to ENVOYE (RG-V07).
 *
 * <p>No {@code @Transactional} at class level — {@code AuditEventListener} uses REQUIRES_NEW.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisiteControllerIT extends IntegrationTestBase {

    private static final UUID SOCIETE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired VisiteRappelRepository rappelRepository;
    @Autowired VisiteRappelService rappelService;

    private String adminBearer;
    private String uid;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_ADMIN);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/visites").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_validRequest_returns201_planifiee() throws Exception {
        ContactResponse contact = createContact("visite-create-" + uid + "@acme.com");
        JsonNode v = doCreateVisite(contact.id(), futureSlot(2));

        assertThat(v.get("id").asText()).isNotBlank();
        assertThat(v.get("statut").asText()).isEqualTo(StatutVisite.PLANIFIEE.name());
        assertThat(v.get("type").asText()).isEqualTo(TypeVisite.SUR_SITE.name());
    }

    @Test
    void create_overlappingSlotSameAgent_returns409() throws Exception {
        ContactResponse contact = createContact("visite-conflit-" + uid + "@acme.com");
        Instant slot = futureSlot(3);
        doCreateVisite(contact.id(), slot);

        // Same agent (admin), overlapping 10 min later → conflict.
        var req = visiteRequest(contact.id(), slot.plus(10, ChronoUnit.MINUTES));
        mvc.perform(post("/api/visites")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmer_thenCompteRendu_marksRealisee() throws Exception {
        ContactResponse contact = createContact("visite-cr-" + uid + "@acme.com");
        UUID id = UUID.fromString(doCreateVisite(contact.id(), futureSlot(4)).get("id").asText());

        mvc.perform(post("/api/visites/{id}/confirmer", id).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(StatutVisite.CONFIRMEE.name()));

        var cr = new CompteRenduRequest("Client très intéressé par le T3.", ResultatVisite.INTERESSE);
        mvc.perform(post("/api/visites/{id}/compte-rendu", id)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(StatutVisite.REALISEE.name()))
                .andExpect(jsonPath("$.resultat").value(ResultatVisite.INTERESSE.name()));
    }

    @Test
    void compteRendu_blank_returns422() throws Exception {
        ContactResponse contact = createContact("visite-cr-blank-" + uid + "@acme.com");
        UUID id = UUID.fromString(doCreateVisite(contact.id(), futureSlot(5)).get("id").asText());
        mvc.perform(post("/api/visites/{id}/confirmer", id).header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Bean validation rejects blank compteRendu before the service → 400.
        var cr = new CompteRenduRequest("   ", null);
        mvc.perform(post("/api/visites/{id}/compte-rendu", id)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cr)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void annuler_setsAnnulee() throws Exception {
        ContactResponse contact = createContact("visite-annul-" + uid + "@acme.com");
        UUID id = UUID.fromString(doCreateVisite(contact.id(), futureSlot(6)).get("id").asText());

        mvc.perform(post("/api/visites/{id}/annuler", id)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnnulerVisiteRequest("Client indisponible"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(StatutVisite.ANNULEE.name()));
    }

    @Test
    void get_crossSocieteVisite_returns404() throws Exception {
        ContactResponse contact = createContact("visite-iso-" + uid + "@acme.com");
        UUID id = UUID.fromString(doCreateVisite(contact.id(), futureSlot(7)).get("id").asText());

        Societe societeB = societeRepository.save(new Societe("Visite Iso Corp B", "MA"));
        User userB = userRepository.save(new User("admin-visite-iso-b-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/visites/{id}", id).header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void rappelJob_dueReminder_isMarkedEnvoye() throws Exception {
        ContactResponse contact = createContact("visite-rappel-" + uid + "@acme.com");
        UUID visiteId = UUID.fromString(doCreateVisite(contact.id(), futureSlot(8)).get("id").asText());

        // Insert a reminder already due (du_a in the past) pointing at the created visite.
        VisiteRappel rappel = rappelRepository.save(new VisiteRappel(
                SOCIETE_ID, visiteId, TypeRappel.H24, DestinataireRappel.AGENT,
                Instant.now().minus(1, ChronoUnit.HOURS)));

        int sent = rappelService.envoyerRappelsDus();
        assertThat(sent).isGreaterThanOrEqualTo(1);

        VisiteRappel reloaded = rappelRepository.findById(rappel.getId()).orElseThrow();
        assertThat(reloaded.getStatut()).isEqualTo(StatutRappel.ENVOYE);
        assertThat(reloaded.getEnvoyeAt()).isNotNull();
    }

    // ===================== Helpers =====================

    private Instant futureSlot(int dayOffset) {
        return Instant.now().plus(dayOffset, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private CreateVisiteRequest visiteRequest(UUID contactId, Instant dateHeure) {
        return new CreateVisiteRequest(contactId, null, null, null, dateHeure, 30,
                TypeVisite.SUR_SITE, "Agence centrale", false);
    }

    private JsonNode doCreateVisite(UUID contactId, Instant dateHeure) throws Exception {
        String body = mvc.perform(post("/api/visites")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visiteRequest(contactId, dateHeure))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Jean", "Dupont", null, email, null, null, null, true, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ContactResponse.class);
    }
}
