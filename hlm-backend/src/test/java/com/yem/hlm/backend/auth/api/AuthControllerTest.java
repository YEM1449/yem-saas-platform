package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.security.CookieTokenHelper;
import com.yem.hlm.backend.auth.service.AuthService;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.usermanagement.InvitationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private CookieTokenHelper cookieTokenHelper;

    @Test
    void login_setsHttpOnlyCookieAndSuppressesTokenFromBody() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(LoginResponse.bearer("test-token", 3600L));
        // CookieTokenHelper returns a real cookie string so the header assertion works
        when(cookieTokenHelper.buildAuthCookie(anyString(), anyLong()))
                .thenReturn(org.springframework.http.ResponseCookie
                        .from(CookieTokenHelper.COOKIE_NAME, "test-token")
                        .httpOnly(true).path("/").maxAge(3600L).build());

        String body = """
            {

              "email": "admin@acme.com",
              "password": "Admin123!Secure"
            }
            """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // Token must NOT appear in the JSON body (httpOnly cookie only)
                .andExpect(jsonPath("$.accessToken").value(""))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                // httpOnly cookie must be set
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("hlm_auth=")));

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(requestCaptor.capture());
        assertThat(requestCaptor.getValue().email()).isEqualTo("admin@acme.com");
        assertThat(requestCaptor.getValue().password()).isEqualTo("Admin123!Secure");
    }
}
