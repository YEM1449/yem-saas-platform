package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests UNITAIRES du JwtAuthenticationFilter.
 *
 * Objectif :
 * - vérifier la logique du filtre (header -> validate -> extract -> set contexts)
 * - vérifier le nettoyage (TenantContext.clear + SecurityContextHolder.clearContext)
 *
 * On n'utilise PAS SpringBootTest : c'est du vrai unit test.
 */
class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        // Hygiène : on nettoie au cas où
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_pass_through_when_no_authorization_header() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // FilterChain mocké : on veut vérifier qu'il est bien appelé
        FilterChain chain = mock(FilterChain.class);

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        verify(chain, times(1)).doFilter(request, response);

        // Aucun appel au jwtProvider car pas de token
        verifyNoInteractions(jwtProvider);

        // Contextes non remplis
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void should_pass_through_when_token_is_invalid() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // jwtProvider.isValid(token) => false
        when(jwtProvider.isValid("invalid.token.here")).thenReturn(false);

        // Act
        filter.doFilter(request, response, chain);

        // Assert : la requête continue
        verify(chain, times(1)).doFilter(request, response);

        // On valide qu'on a juste check isValid, sans extractions
        verify(jwtProvider, times(1)).isValid("invalid.token.here");
        verify(jwtProvider, never()).extractUserId(anyString());
        verify(jwtProvider, never()).extractTenantId(anyString());

        // Contextes doivent rester vides
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void should_set_contexts_when_token_is_valid_and_clear_after_chain() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good.token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // jwt valide
        when(jwtProvider.isValid("good.token")).thenReturn(true);

        // extraction claims
        when(jwtProvider.extractUserId("good.token")).thenReturn(userId);
        when(jwtProvider.extractTenantId("good.token")).thenReturn(tenantId);

        // On mock la chain pour vérifier :
        // - que pendant l'exécution de la chain, les contextes sont bien remplis
        FilterChain chain = (req, res) -> {
            // Assert "pendant" la requête : TenantContext rempli
            assertEquals(userId, TenantContext.getUserId());
            assertEquals(tenantId, TenantContext.getTenantId());

            // Assert "pendant" la requête : SecurityContext rempli
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(userId.toString(), SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        };

        // Act
        filter.doFilter(request, response, chain);

        // Assert "après" la requête : nettoyage effectué
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Et on vérifie les appels au provider
        verify(jwtProvider, times(1)).isValid("good.token");
        verify(jwtProvider, times(1)).extractUserId("good.token");
        verify(jwtProvider, times(1)).extractTenantId("good.token");
    }
}
