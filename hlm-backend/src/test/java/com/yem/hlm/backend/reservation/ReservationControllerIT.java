package com.yem.hlm.backend.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.ContactResponse;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.reservation.api.dto.CreateReservationRequest;
import com.yem.hlm.backend.reservation.api.dto.ReservationResponse;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code /api/reservations}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Auth guards (401/403)</li>
 *   <li>Create → list → get lifecycle</li>
 *   <li>Cancel ACTIVE reservation (property released back to ACTIVE)</li>
 *   <li>Conflict guard — cannot reserve an already-RESERVED property</li>
 *   <li>Cross-société isolation — société B cannot see société A's reservations</li>
 * </ul>
 *
 * <p>No {@code @Transactional} at class level — {@link com.yem.hlm.backend.audit.AuditEventListener}
 * uses {@code Propagation.REQUIRES_NEW} which cannot see uncommitted test data.
 * UIDs are appended to all emails to avoid unique-constraint collisions across tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerIT extends IntegrationTestBase {

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
    // Auth guards
    // =========================================================================

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asAgent_returns403() throws Exception {
        User agentUser = userRepository.save(new User("agent-" + uid + "@acme.com", "hashed"));
        String agentBearer = "Bearer " + jwtProvider.generate(agentUser.getId(), SOCIETE_ID, UserRole.ROLE_AGENT);

        mvc.perform(post("/api/reservations")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactId\":\"" + UUID.randomUUID() + "\",\"propertyId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Create → list → get lifecycle
    // =========================================================================

    @Test
    void create_validRequest_returns201_andPropertyIsReserved() throws Exception {
        ContactResponse contact = createContact("res-create-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();

        ReservationResponse reservation = doCreate(contact.id(), propertyId, null);

        assertThat(reservation.id()).isNotNull();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.propertyId()).isEqualTo(propertyId);
        assertThat(reservation.contactId()).isEqualTo(contact.id());

        // Property must now be RESERVED
        PropertyResponse prop = getProperty(propertyId);
        assertThat(prop.status()).isEqualTo(PropertyStatus.RESERVED);
    }

    @Test
    void list_returnsCreatedReservation() throws Exception {
        ContactResponse contact = createContact("res-list-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        doCreate(contact.id(), propertyId, null);

        mvc.perform(get("/api/reservations").header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.propertyId == '" + propertyId + "')]").exists());
    }

    @Test
    void get_returnsCorrectReservation() throws Exception {
        ContactResponse contact = createContact("res-get-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        ReservationResponse created = doCreate(contact.id(), propertyId, null);

        mvc.perform(get("/api/reservations/{id}", created.id()).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.status").value(ReservationStatus.ACTIVE.name()));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/reservations/{id}", UUID.randomUUID()).header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Cancel
    // =========================================================================

    @Test
    void cancel_activeReservation_returns200_andReleasesProperty() throws Exception {
        ContactResponse contact = createContact("res-cancel-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        ReservationResponse created = doCreate(contact.id(), propertyId, null);

        // Property is RESERVED at this point
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.RESERVED);

        // Cancel
        mvc.perform(post("/api/reservations/{id}/cancel", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReservationStatus.CANCELLED.name()));

        // Property must be released back to ACTIVE
        assertThat(getProperty(propertyId).status()).isEqualTo(PropertyStatus.ACTIVE);
    }

    @Test
    void cancel_alreadyCancelledReservation_returns409() throws Exception {
        ContactResponse contact = createContact("res-double-cancel-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();
        ReservationResponse created = doCreate(contact.id(), propertyId, null);

        mvc.perform(post("/api/reservations/{id}/cancel", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk());

        // Second cancel must fail
        mvc.perform(post("/api/reservations/{id}/cancel", created.id())
                        .header("Authorization", adminBearer))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Conflict guard — already-reserved property
    // =========================================================================

    @Test
    void create_onAlreadyReservedProperty_returns409() throws Exception {
        ContactResponse contactA = createContact("res-conflict-a-" + uid + "@acme.com");
        ContactResponse contactB = createContact("res-conflict-b-" + uid + "@acme.com");
        UUID propertyId = createActiveProperty();

        // First reservation succeeds
        doCreate(contactA.id(), propertyId, null);

        // Second reservation on same property fails
        mvc.perform(post("/api/reservations")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(contactB.id(), propertyId, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_withNonExistentProperty_returns404() throws Exception {
        ContactResponse contact = createContact("res-noprop-" + uid + "@acme.com");

        mvc.perform(post("/api/reservations")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(contact.id(), UUID.randomUUID(), null, null, null))))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Cross-société isolation
    // =========================================================================

    @Test
    void get_crossSocieteReservation_returns404() throws Exception {
        // Create reservation in société A
        ContactResponse contactA = createContact("res-iso-a-" + uid + "@acme.com");
        UUID propertyA = createActiveProperty();
        ReservationResponse reservationA = doCreate(contactA.id(), propertyA, null);

        // Create société B + user
        Societe societeB = societeRepository.save(new Societe("Isolation Corp B", "MA"));
        User userB = userRepository.save(new User("admin-res-iso-b-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        // Société B cannot see société A's reservation
        mvc.perform(get("/api/reservations/{id}", reservationA.id())
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_crossSocieteReservation_notVisible() throws Exception {
        // Create reservation in société A
        ContactResponse contactA = createContact("res-iso-list-a-" + uid + "@acme.com");
        UUID propertyA = createActiveProperty();
        doCreate(contactA.id(), propertyA, null);

        // Create société B + user (empty société)
        Societe societeB = societeRepository.save(new Societe("Empty Corp B", "MA"));
        User userB = userRepository.save(new User("admin-res-list-b-" + uid + "@corp.com", "hashed"));
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);

        // Société B's list must be empty
        mvc.perform(get("/api/reservations").header("Authorization", bearerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ReservationResponse doCreate(UUID contactId, UUID propertyId, BigDecimal price) throws Exception {
        String body = mvc.perform(post("/api/reservations")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(contactId, propertyId, price, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ReservationResponse.class);
    }

    private ContactResponse createContact(String email) throws Exception {
        var req = new CreateContactRequest("Jean", "Dupont", null, email, null, null, null, null, null, null);
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
        String ref = "RES-TEST-" + uid + "-" + (++counter);
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

        // Activate: DRAFT → ACTIVE
        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }
}
