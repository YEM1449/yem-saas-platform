package com.yem.hlm.backend.contact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.*;
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

@SpringBootTest
@AutoConfigureMockMvc
class ContactControllerIT extends IntegrationTestBase {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;

    private String bearer;

    @BeforeEach
    void setupToken() {
        bearer = "Bearer " + jwtProvider.generate(USER_ID, TENANT_ID);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContactRequest(
                                "John", "Doe", null, null, null, null, null
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withToken_returns201_andProspect() throws Exception {
        var req = new CreateContactRequest("John", "Doe", "0612", "john@acme.com", null, null, null);

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
        var createReq = new CreateContactRequest("Alice", "Smith", null, "alice@acme.com", null, null, null);
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
        var createReq = new CreateContactRequest("Yass", "B", null, "yass@acme.com", null, null, null);
        String json = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ContactResponse created = objectMapper.readValue(json, ContactResponse.class);

        UUID propertyId = UUID.randomUUID();
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
    void unknownTenant_returns403() throws Exception {
        UUID unknownTenant = UUID.randomUUID();
        String badBearer = "Bearer " + jwtProvider.generate(USER_ID, unknownTenant);

        mvc.perform(post("/api/contacts")
                        .header("Authorization", badBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateContactRequest(
                                "Bad", "Tenant", null, "bad@tenant.com", null, null, null
                        ))))
                .andExpect(status().isForbidden());
    }
}
