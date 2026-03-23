package com.yem.hlm.backend.societe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.CreateContactRequest;
import com.yem.hlm.backend.project.api.dto.ProjectCreateRequest;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that resources created by société A are not visible to société B.
 *
 * <p>Each test:
 * <ol>
 *   <li>Creates a resource as bearerA (société A admin).</li>
 *   <li>Tries to read/use that resource with bearerB (société B admin).</li>
 *   <li>Expects 404 — resource does not exist in société B's scope.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrossSocieteIsolationIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String bearerA;
    private String bearerB;

    @BeforeEach
    void setup() {
        Societe societeA = societeRepository.save(new Societe("Société Alpha", "MA"));
        Societe societeB = societeRepository.save(new Societe("Société Beta", "MA"));

        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User userA = userRepository.save(new User("admin-" + uid + "@alpha.test", "hash"));
        User userB = userRepository.save(new User("admin-" + uid + "@beta.test", "hash"));

        bearerA = "Bearer " + jwtProvider.generate(userA.getId(), societeA.getId(), UserRole.ROLE_ADMIN);
        bearerB = "Bearer " + jwtProvider.generate(userB.getId(), societeB.getId(), UserRole.ROLE_ADMIN);
    }

    // ── Contacts ──────────────────────────────────────────────────────

    @Test
    void contact_createdBySocieteA_notFoundBySocieteB() throws Exception {
        // Create contact as societe A
        String body = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateContactRequest(
                                "Isolée", "Contact", null, "iso@alpha.test",
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String contactId = json.readTree(body).get("id").asText();

        // Societe B must not see it
        mvc.perform(get("/api/contacts/{id}", contactId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void contactList_societeB_doesNotContainSocieteAContacts() throws Exception {
        // Create contact as societe A
        mvc.perform(post("/api/contacts")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateContactRequest(
                                "Alpha", "Only", null, "alpha-only@alpha.test",
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated());

        // Societe B list must be empty (no contacts created for B)
        String listBody = mvc.perform(get("/api/contacts")
                        .header("Authorization", bearerB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The list (pageable) should have totalElements == 0
        long total = json.readTree(listBody).get("page").get("totalElements").asLong();
        org.assertj.core.api.Assertions.assertThat(total).isZero();
    }

    // ── Projects ──────────────────────────────────────────────────────

    @Test
    void project_createdBySocieteA_notFoundBySocieteB() throws Exception {
        String body = mvc.perform(post("/api/projects")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ProjectCreateRequest("Alpha Project", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String projectId = json.readTree(body).get("id").asText();

        mvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ── Properties ────────────────────────────────────────────────────

    @Test
    void property_createdBySocieteA_notFoundBySocieteB() throws Exception {
        // First create a project in societe A
        String projBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ProjectCreateRequest("Alpha Proj", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String projectId = json.readTree(projBody).get("id").asText();

        // Create property in societe A (COMMERCE: requires surfaceAreaSqm)
        String propertyJson = """
                {"type":"COMMERCE","title":"Isolated Commerce","referenceCode":"ISO-001",
                 "price":200000,"currency":"MAD","surfaceAreaSqm":80,"projectId":"%s"}
                """.formatted(projectId);

        String propBody = mvc.perform(post("/api/properties")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String propertyId = json.readTree(propBody).get("id").asText();

        // Societe B must not see it
        mvc.perform(get("/api/properties/{id}", propertyId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ── Contact timeline cross-access ─────────────────────────────────

    @Test
    void contactTimeline_societeB_returns404ForSocieteAContact() throws Exception {
        String body = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateContactRequest(
                                "Timeline", "Alpha", null, "timeline@alpha.test",
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String contactId = json.readTree(body).get("id").asText();

        mvc.perform(get("/api/contacts/{id}/timeline", contactId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }
}
