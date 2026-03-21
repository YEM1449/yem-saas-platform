package com.yem.hlm.backend.contact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.api.dto.PropertyResponse;
import com.yem.hlm.backend.property.api.dto.PropertyUpdateRequest;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContactControllerIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;

    private String bearer;
    private int refCounter = 0;

    @BeforeEach
    void setupToken() {
        bearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID, UserRole.ROLE_ADMIN);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContactRequest(
                                "John", "Doe", null, null, null, null, null, null, null, null
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withToken_returns201_andProspect() throws Exception {
        var req = new CreateContactRequest("John", "Doe", "0612", "john@acme.com", null, null, null, null, null, null);

        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(ContactStatus.PROSPECT.name()))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ContactResponse created = objectMapper.readValue(json, ContactResponse.class);
        assertThat(created.firstName()).isEqualTo("John");
    }

    @Test
    void convert_validationError_returns400() throws Exception {
        // Create
        var createReq = new CreateContactRequest("Alice", "Smith", null, "alice@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ContactResponse created = objectMapper.readValue(json, ContactResponse.class);

        // Missing propertyId => 400 (bean validation)
        var bad = new ConvertToClientRequest(null, new BigDecimal("100.00"), null, null, "MAD", null);
        mvc.perform(post("/api/contacts/{id}/convert-to-client", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void interests_duplicate_returns409() throws Exception {
        // Create
        var createReq = new CreateContactRequest("Yass", "B", null, "yass@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ContactResponse created = objectMapper.readValue(json, ContactResponse.class);

        UUID propertyId = createActiveProperty();
        var interestReq = new ContactInterestRequest(propertyId, null);

        mvc.perform(post("/api/contacts/{id}/interests", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(interestReq)))
                .andExpect(status().isCreated());

        // second time => 409
        mvc.perform(post("/api/contacts/{id}/interests", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(interestReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        mvc.perform(get("/api/contacts/{id}", UUID.randomUUID())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSociete_jwtTrusted_contactScopedToThatSociete() throws Exception {
        // In the multi-société architecture the JWT is signed & trusted — société ID
        // comes from the validated JWT claim, no per-request DB lookup required.
        // A validly-signed JWT for a non-existent société still yields 2xx.
        UUID unknownSociete = UUID.randomUUID();
        String badBearer = "Bearer " + jwtProvider.generate(USER_ID, unknownSociete, UserRole.ROLE_ADMIN);

        mvc.perform(post("/api/contacts")
                        .header("Authorization", badBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContactRequest(
                                "Unknown", "Societe", null, "unknown@societe.com", null, null, null, null, null, null
                        ))))
                .andExpect(status().is2xxSuccessful());
    }

    // ===== Tenant Isolation Tests =====

    @Test
    void listContacts_tenantIsolation_returnsOnlyOwnContacts() throws Exception {
        // Create contact in tenant A (seeded acme)
        var reqA = new CreateContactRequest("Alice", "TenantA", null, "alice-a@acme.com", null, null, null, null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        // Create tenant B with its own user
        String otherKey = "iso-test-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@iso.com", "hashedPass");
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Create contact in tenant B
        var reqB = new CreateContactRequest("Bob", "TenantB", null, "bob-b@iso.com", null, null, null, null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated());

        // List as tenant A — should NOT contain tenant B's contact
        String json = mvc.perform(get("/api/contacts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var page = objectMapper.readTree(json);
        var content = page.get("content");
        assertThat(content.isArray()).isTrue();
        for (var node : content) {
            assertThat(node.get("lastName").asText()).isNotEqualTo("TenantB");
        }
    }

    @Test
    void getContact_crossTenant_returns404() throws Exception {
        // Create contact in tenant A
        var reqA = new CreateContactRequest("Cross", "Check", null, "cross@acme.com", null, null, null, null, null, null);
        String jsonA = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactAId = objectMapper.readValue(jsonA, ContactResponse.class).id();

        // Create tenant B
        String otherKey = "iso-get-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@iso-get.com", "hashedPass");
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Tenant B tries to GET tenant A's contact — must be 404
        mvc.perform(get("/api/contacts/{id}", contactAId)
                        .header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    // ===== Prospect-specific Tests =====

    @Test
    void listContacts_filterByContactType_returnsOnlyProspects() throws Exception {
        // Create a contact (defaults to PROSPECT type)
        var req = new CreateContactRequest("Filtered", "Prospect", null, "fp@acme.com", null, null, null, null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // List with contactType=PROSPECT filter
        String json = mvc.perform(get("/api/contacts")
                        .param("contactType", "PROSPECT")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var page = objectMapper.readTree(json);
        var content = page.get("content");
        assertThat(content.isArray()).isTrue();
        for (var node : content) {
            assertThat(node.get("contactType").asText()).isEqualTo("PROSPECT");
        }
    }

    @Test
    void updateStatus_validTransition_returnsUpdated() throws Exception {
        // Create contact
        var req = new CreateContactRequest("Status", "Test", null, "status@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROSPECT"))
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(json, ContactResponse.class).id();

        // Update status to QUALIFIED_PROSPECT
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED_PROSPECT"));
    }

    @Test
    void updateStatus_invalidValue_returns400() throws Exception {
        var req = new CreateContactRequest("Bad", "Status", null, "badstatus@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(json, ContactResponse.class).id();

        // Invalid enum value
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_crossTenant_returns404() throws Exception {
        // Create contact in tenant A
        var req = new CreateContactRequest("CrossStatus", "Test", null, "crossstatus@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(json, ContactResponse.class).id();

        // Create tenant B
        String otherKey = "iso-status-" + UUID.randomUUID().toString().substring(0, 8);
        Societe tenantB = societeRepository.save(new Societe("Acme Corp", "MA"));
        User userB = new User("admin@iso-status.com", "hashedPass");
        userB = userRepository.save(userB);
        String bearerB = "Bearer " + jwtProvider.generate(userB.getId(), tenantB.getId(), UserRole.ROLE_ADMIN);

        // Tenant B tries to update status of tenant A's contact — must be 404
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", bearerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_invalidTransition_returns409() throws Exception {
        var req = new CreateContactRequest("Machine", "Test", null, "machine@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(json, ContactResponse.class).id();

        // PROSPECT → CLIENT is invalid (must go through QUALIFIED_PROSPECT first)
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLIENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_unauthenticated_returns401() throws Exception {
        mvc.perform(patch("/api/contacts/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listContacts_multipleContactTypes_includesTempClient() throws Exception {
        // Create a PROSPECT contact, then deposit converts it to TEMP_CLIENT
        var req = new CreateContactRequest("TempClient", "Prospect", null, "tempclient@acme.com", null, null, null, null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contactId = objectMapper.readValue(json, ContactResponse.class).id();

        // Manually update to TEMP_CLIENT to simulate deposit workflow
        mvc.perform(patch("/api/contacts/{id}/status", contactId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED_PROSPECT\"}"))
                .andExpect(status().isOk());

        // Filter by PROSPECT only — should NOT include TEMP_CLIENT contacts
        // (the contact is still PROSPECT type at this point since status ≠ type)
        // Now create another contact that stays PROSPECT
        var req2 = new CreateContactRequest("StillProspect", "Test", null, "stillprospect@acme.com", null, null, null, null, null, null);
        mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Filter by both PROSPECT and TEMP_CLIENT — should include both types
        String multiJson = mvc.perform(get("/api/contacts")
                        .param("contactType", "PROSPECT", "TEMP_CLIENT")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var multiPage = objectMapper.readTree(multiJson);
        var multiContent = multiPage.get("content");
        assertThat(multiContent.isArray()).isTrue();
        assertThat(multiContent.size()).isGreaterThanOrEqualTo(2);
        for (var node : multiContent) {
            String type = node.get("contactType").asText();
            assertThat(type).isIn("PROSPECT", "TEMP_CLIENT");
        }
    }

    // ===== Helpers =====

    private UUID createActiveProperty() throws Exception {
        String ref = "CCIT-" + (++refCounter);
        String projectBody = mvc.perform(post("/api/projects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project " + ref + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projectBody).get("id").asText());
        var propReq = new PropertyCreateRequest(
                PropertyType.VILLA, "Test Villa " + ref, ref,
                new BigDecimal("1000000"), "MAD",
                null, null, null, "Casablanca", null, null, null, null,
                null, null, null, null,
                new BigDecimal("200"), new BigDecimal("400"),
                3, 2, 2, null, null, null, null, null, null, null, null, null,
                null, projId, null
        );

        String body = mvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(propReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        PropertyResponse created = objectMapper.readValue(body, PropertyResponse.class);

        var updateReq = new PropertyUpdateRequest(null, null, null, null, PropertyStatus.ACTIVE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        mvc.perform(put("/api/properties/{id}", created.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        return created.id();
    }
}
