package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.auth.service.UserSecurityInfo;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests UNITAIRES du {@link JwtAuthenticationFilter}.
 *
 * Objectifs :
 * - Vérifier la logique du filtre (header -> validate -> extract -> set contexts)
 * - Vérifier le nettoyage (TenantContext.clear + SecurityContextHolder.clearContext)
 *
 * NOTE : on n'utilise PAS SpringBootTest ici : c'est un vrai unit test.
 */
class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        // Hygiène : on nettoie au cas où un test aurait échoué avant la fin
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_pass_through_when_no_authorization_header() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, mock(UserSecurityCacheService.class), new com.yem.hlm.backend.auth.service.SecurityAuditLogger());

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
    void should_pass_through_when_authorization_header_is_not_bearer() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, mock(UserSecurityCacheService.class), new com.yem.hlm.backend.auth.service.SecurityAuditLogger());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Act
        filter.doFilter(request, response, chain);

        // Assert : la requête continue
        verify(chain, times(1)).doFilter(request, response);

        // Aucun appel au jwtProvider car ce n'est pas un Bearer token
        verifyNoInteractions(jwtProvider);

        // Contextes doivent rester vides
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void should_pass_through_when_token_is_invalid() throws ServletException, IOException {
        // Arrange
        JwtProvider jwtProvider = mock(JwtProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, mock(UserSecurityCacheService.class), new com.yem.hlm.backend.auth.service.SecurityAuditLogger());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // jwtProvider.isValid(token) => false
        when(jwtProvider.isValid("invalid.token.here")).thenReturn(false);

        // Act
        filter.doFilter(request, response, chain);

        // Assert : la requête continue (le filtre ne bloque pas, il laisse Spring Security gérer)
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
        UserSecurityCacheService cacheService = mock(UserSecurityCacheService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, cacheService, new com.yem.hlm.backend.auth.service.SecurityAuditLogger());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good.token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // IDs déterministes => debug plus simple si un test casse
        UUID userId = UUID.fromString("8f33b2c6-8c2c-4cf1-8a87-7cc5c2d9f0a1");
        UUID tenantId = UUID.fromString("aa460c78-2b1b-4374-bfb3-3c04e5b82455");

        // jwt valide
        when(jwtProvider.isValid("good.token")).thenReturn(true);

        // extraction claims (Option A : on travaille en UUID côté app)
        when(jwtProvider.extractUserId("good.token")).thenReturn(userId);
        when(jwtProvider.extractTenantId("good.token")).thenReturn(tenantId);

        // tokenVersion check: token has tv=0, cache returns matching info
        when(jwtProvider.extractTokenVersion("good.token")).thenReturn(0);
        when(cacheService.getSecurityInfo(userId)).thenReturn(new UserSecurityInfo(true, 0));

        // On mock la chain pour vérifier :
        // - que pendant l'exécution de la chain, les contextes sont bien remplis
        FilterChain chain = (req, res) -> {
            // Assert "pendant" la requête : TenantContext rempli
            assertEquals(userId, TenantContext.getUserId());
            assertEquals(tenantId, TenantContext.getTenantId()); // <-- Option A : compare UUID vs UUID

            // Assert "pendant" la requête : SecurityContext rempli
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());

            // Selon ton implémentation, le principal est souvent un String (ex: userId.toString()).
            // On garde cette assertion telle quelle pour matcher l'implémentation précédente.
            // Principal peut être un UUID (selon ton implémentation) : on aligne le test.
            assertEquals(userId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        };

        // Act
        filter.doFilter(request, response, chain);

        // Assert "après" la requête : nettoyage TenantContext effectué (critique en multi-tenant + ThreadLocal)
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());

        // NOTE: Ici on exécute le filtre "tout seul" (unit test). En prod, c'est le filtre/framework Spring Security
        // (ex: SecurityContextHolderFilter) qui nettoie le SecurityContext en fin de requête.
        // Donc dans ce test, on vérifie que l'auth a bien été posée, puis on nettoie nous-mêmes pour éviter toute fuite entre tests.
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Et on vérifie les appels au provider
        verify(jwtProvider, times(1)).isValid("good.token");
        verify(jwtProvider, times(1)).extractUserId("good.token");
        verify(jwtProvider, times(1)).extractTenantId("good.token");
// Et on vérifie les appels au provider
        verify(jwtProvider, times(1)).isValid("good.token");
        verify(jwtProvider, times(1)).extractUserId("good.token");
        verify(jwtProvider, times(1)).extractTenantId("good.token");
    }
}
