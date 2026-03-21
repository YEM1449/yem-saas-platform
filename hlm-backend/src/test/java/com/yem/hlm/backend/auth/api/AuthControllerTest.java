package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.service.AuthService;
import com.yem.hlm.backend.auth.service.JwtProvider;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void login_returnsAccessToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(LoginResponse.bearer("test-token", 3600L));

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
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(requestCaptor.capture());
        assertThat(requestCaptor.getValue().email()).isEqualTo("admin@acme.com");
        assertThat(requestCaptor.getValue().password()).isEqualTo("Admin123!Secure");
    }
}
