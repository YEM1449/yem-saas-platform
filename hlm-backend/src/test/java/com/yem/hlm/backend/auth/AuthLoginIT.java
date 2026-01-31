package com.yem.hlm.backend.auth;

import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for Auth login endpoint.
 *
 * What we validate here:
 * - Spring context loads (web + security)
 * - DB is real (Testcontainers Postgres)
 * - Liquibase migrations ran
 * - /auth/login returns a JWT on valid credentials
 */
class AuthLoginIT extends IntegrationTestBase { //hérites d’un environnement de test complet()

    @Autowired
    MockMvc mockMvc;
    /*➡️ MockMvc permet de simuler un appel HTTP sans lancer un vrai serveur sur un port fixe, mais en utilisant tout Spring MVC + Spring Security.*/

    @Test
    void login_ok_returnsAccessToken() throws Exception {

        // This JSON must match your LoginRequest fields.
        // Adapt keys if needed (tenantKey/email/password).
        //➡️ On prépare un JSON identique à ce qu’un frontend enverrait.
        String body = """
            {
              "tenantKey": "acme",
              "email": "admin@acme.com",
              "password": "Admin123!"
            }
            """;

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)//➡️ Indique à Spring comment parser le body (sinon tu peux avoir 415 Unsupported Media Type).
                                .accept(MediaType.APPLICATION_JSON)//➡️ Indique ce qu’on attend en réponse.
                                .content(body)//➡️ Injecte le JSON dans la requête.
                )
                .andExpect(status().isOk())//➡️ Valide que l’endpoint a réussi (HTTP 200).
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // adapt if your response field is "token" or "jwt"
                .andExpect(jsonPath("$.accessToken").isNotEmpty());//➡️ Vérifie que ta réponse contient bien le JWT.
    }
    @Test
    void login_wrongPassword_returns401() throws Exception {
        String body = """
            {
              "tenantKey": "acme",
              "email": "admin@acme.com",
              "password": "WrongPassword!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized()); // ✅ 401
    }
    @Test
    void login_unknownEmail_returns401() throws Exception {
        String body = """
            {
              "tenantKey": "acme",
              "email": "nobody@acme.com",
              "password": "Admin123!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized()); // ✅ 401
    }
    @Test
    void login_wrongTenant_returns401() throws Exception {
        String body = """
            {
              "tenantKey": "wrongTenant",
              "email": "admin@acme.com",
              "password": "Admin123!"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized()); // ✅ 401
    }

    @Test
    void login_get_notPermitted_returns401() throws Exception {
        mockMvc.perform(get("/auth/login")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
