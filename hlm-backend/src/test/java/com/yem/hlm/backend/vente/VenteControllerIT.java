package com.yem.hlm.backend.vente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyStatusUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.vente.api.dto.CreateVenteRequest;
import com.yem.hlm.backend.vente.api.dto.UpdateVenteStatutRequest;
import com.yem.hlm.backend.vente.domain.MotifAnnulation;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code /api/ventes} — the commercial pipeline core.
 *
 * <p>Covers:
 * <ul>
 *   <li>Auth guard (401)</li>
 *   <li>Create → property becomes RESERVED</li>
 *   <li><b>RG-B03</b> — a 2nd active vente on the same property is rejected (409)</li>
 *   <li>RG-B04 — valid transition (200) and invalid skip-a-stage transition (409)</li>
 *   <li>Cancelling a vente (ANNULE) frees the property for a new vente</li>
 *   <li>Cross-société isolation — société B cannot see société A's vente (404)</li>
 *   <li>Non-existent property → 404</li>
 * </ul>
 *
 * <p>No {@code @Transactional} at class level — {@link com.yem.hlm.backend.audit.AuditEventListener}
 * uses {@code Propagation.REQUIRES_NEW}. UIDs are appended to all emails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VenteControllerIT extends IntegrationTestBase {

    private static final UUID SOCIETE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String adminBearer;
    private String uid;
    private int counter = 0;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_ADMIN);
    }

    // =========================================================================
    // Auth
    // =========================================================================

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/ventes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Create lifecycle
    // =========================================================================

    @Test
    void create_validRequest_returns201_andPropertyBecomesReserved() throws Exception {
        ContactResponse contact = createContact("vente-create-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();

        JsonNode vente = doCreateVente(contact.id(), propertyId);

        assertThat(vente.get("id").asText()).isNotBlank();
        assertThat(vente.get("statut").asText()).isEqualTo(VenteStatut.COMPROMIS.name());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);
    }

    // =========================================================================
    // RG-B03 — one active vente per property
    // =========================================================================

    @Test
    void create_secondVenteOnSameProperty_returns409() throws Exception {
        ContactResponse contactA = createContact("vente-dup-a-" + uid + "@acme.com");
        ContactResponse contactB = createContact("vente-dup-b-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();

        doCreateVente(contactA.id(), propertyId); // first sale → property RESERVED

        // Second active vente on the same property must be rejected (RG-B03)
        mvc.perform(post("/api/ventes")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(venteRequest(contactB.id(), propertyId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPERTY_ALREADY_ENGAGED"));
    }

    @Test
    void cancelledVente_freesPropertyForNewVente() throws Exception {
        ContactResponse contactA = createContact("vente-free-a-" + uid + "@acme.com");
        ContactResponse contactB = createContact("vente-free-b-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();

        JsonNode first = doCreateVente(contactA.id(), propertyId);
        UUID firstId = UUID.fromString(first.get("id").asText());

        // Cancel the first vente → property released back to ACTIVE
        mvc.perform(patch("/api/ventes/{id}/statut", firstId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVenteStatutRequest(
                                VenteStatut.ANNULE, MotifAnnulation.AUTRE, null, null, "test", null))))
                .andExpect(status().isOk());
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.ACTIVE);

        // A new vente on the now-free property succeeds (ANNULE is excluded from the guard)
        doCreateVente(contactB.id(), propertyId);
    }

    // =========================================================================
    // RG-B04 — state machine
    // =========================================================================

    @Test
    void updateStatut_validTransition_returns200() throws Exception {
        ContactResponse contact = createContact("vente-tr-ok-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        UUID venteId = UUID.fromString(doCreateVente(contact.id(), propertyId).get("id").asText());

        mvc.perform(patch("/api/ventes/{id}/statut", venteId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVenteStatutRequest(
                                VenteStatut.FINANCEMENT, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(VenteStatut.FINANCEMENT.name()));
    }

    @Test
    void updateStatut_skippingStages_returns409() throws Exception {
        ContactResponse contact = createContact("vente-tr-ko-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        UUID venteId = UUID.fromString(doCreateVente(contact.id(), propertyId).get("id").asText());

        // COMPROMIS → LIVRE skips FINANCEMENT/ACTE_NOTARIE → invalid
        mvc.perform(patch("/api/ventes/{id}/statut", venteId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVenteStatutRequest(
                                VenteStatut.LIVRE_DEFINITIF, null, null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // Not found / isolation
    // =========================================================================

    @Test
    void create_withNonExistentProperty_returns404() throws Exception {
        ContactResponse contact = createContact("vente-noprop-" + uid + "@acme.com");

        mvc.perform(post("/api/ventes")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(venteRequest(contact.id(), UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossSocieteVente_returns404() throws Exception {
        ContactResponse contactA = createContact("vente-iso-a-" + uid + "@acme.com");
        UUID propertyA = createActiveProperty();
        UUID venteA = UUID.fromString(doCreateVente(contactA.id(), propertyA).get("id").asText());

        Societe societeB = societeRepository.save(new Societe("Vente Isolation Corp B", "MA"));
        User userB = userRepository.save(new User("admin-vente-iso-b-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        mvc.perform(get("/api/ventes/{id}", venteA).header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CreateVenteRequest venteRequest(UUID contactId, UUID propertyId) {
        return new CreateVenteRequest(
                null, contactId, propertyId, null,
                new BigDecimal("900000"), null,
                LocalDate.now(), null, null, "IT vente");
    }

    private JsonNode doCreateVente(UUID contactId, UUID propertyId) throws Exception {
        String body = mvc.perform(post("/api/ventes")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(venteRequest(contactId, propertyId))))
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

    private PropertyResponse getProperty(UUID id) throws Exception {
        String json = mvc.perform(get("/api/properties/{id}", id).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PropertyResponse.class);
    }

    private UUID createActiveProperty() throws Exception {
        String ref = "VENTE-TEST-" + uid + "-" + (++counter);
        String projJson = mvc.perform(post("/api/projects")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projJson).get("id").asText());

        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "Test Villa " + ref, ref,
                new BigDecimal("1000000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("200"), new BigDecimal("400"),
                3, 2, 2, null, null, null, null, null, null, null, null, null,
                null, projId, null, null
        );
        String propJson = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PropertyResponse created = objectMapper.readValue(propJson, PropertyResponse.class);

        mvc.perform(patch("/api/properties/{id}/status", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PropertyStatusUpdateRequest(PropertyStatus.ACTIVE))))
                .andExpect(status().isOk());

        return created.id();
    }
}
