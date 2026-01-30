package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtProvider.
 *
 * Ici on teste uniquement la logique métier :
 * - quels claims sont mis
 * - quel comportement si decoder throw
 *
 * On ne teste PAS la crypto réelle (c'est pour des tests d'intégration).
 */
@ExtendWith(MockitoExtension.class) // Active Mockito avec JUnit5 (injection des @Mock)
class JwtProviderTest {

    @Mock
    JwtEncoder encoder; // mock du composant Spring Security qui "signe" le JWT

    @Mock
    JwtDecoder decoder; // mock du composant qui "décode/valide" le JWT

    @Mock
    JwtProperties props; // mock des properties (ttlSeconds)

    JwtProvider provider; // SUT = System Under Test (la classe qu'on teste)

    @BeforeEach
    void setUp() {
        // On fixe un TTL stable pour les tests (ex: 3600s)
        when(props.ttlSeconds()).thenReturn(3600L);

        // On instancie JwtProvider "à la main" (unit test, pas de Spring context)
        provider = new JwtProvider(encoder, decoder, props);
    }

    @Test
    void generate_shouldReturnTokenValue_andPutSubjectAndTidClaims() {
        // Arrange (préparation)
        UUID userId = UUID.randomUUID();   // userId attendu dans subject
        UUID tenantId = UUID.randomUUID(); // tenantId attendu dans claim "tid"

        // On veut capturer les paramètres passés à encoder.encode(...)
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);

        // Quand encoder.encode(...) est appelé, on renvoie un Jwt fake avec tokenValue.
        // Important: encoder.encode retourne un Jwt, pas un String.
        when(encoder.encode(captor.capture()))
                .thenReturn(new Jwt(
                        "fake-token",            // tokenValue (ce que generate() doit retourner)
                        Instant.now(),           // issuedAt (pas critique ici)
                        Instant.now().plusSeconds(3600), // expiresAt (pas critique ici)
                        Map.of("alg", "HS256"),  // headers (dummy)
                        Map.of("sub", "x")       // claims (dummy)
                ));

        // Act (exécution)
        String token = provider.generate(userId, tenantId);

        // Assert (vérifications)
        assertEquals("fake-token", token, "generate() doit retourner le tokenValue fourni par JwtEncoder");

        // On récupère les claims réellement construits par JwtProvider
        JwtClaimsSet claims = captor.getValue().getClaims();

        // subject doit être le userId stringifié
        assertEquals(userId.toString(), claims.getSubject(), "subject doit contenir userId");

        // claim custom "tid" doit exister et contenir tenantId
        assertEquals(tenantId.toString(), claims.getClaim("tid"), "claim 'tid' doit contenir tenantId");

        // issuedAt/expiresAt : on vérifie juste qu'ils existent et sont cohérents
        assertNotNull(claims.getIssuedAt(), "issuedAt doit être défini");
        assertNotNull(claims.getExpiresAt(), "expiresAt doit être défini");
        assertTrue(claims.getExpiresAt().isAfter(claims.getIssuedAt()), "expiresAt doit être après issuedAt");
    }

    @Test
    void isValid_shouldReturnTrue_whenDecoderDecodesSuccessfully() {
        // Arrange
        String token = "any-token";

        // decoder.decode(...) doit réussir => pas d'exception
        when(decoder.decode(token))
                .thenReturn(new Jwt(
                        token,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of("alg", "HS256"),
                        Map.of("sub", UUID.randomUUID().toString(), "tid", UUID.randomUUID().toString())
                ));

        // Act
        boolean valid = provider.isValid(token);

        // Assert
        assertTrue(valid, "isValid() doit renvoyer true si decoder.decode() ne jette pas d'exception");
        verify(decoder).decode(token); // confirme qu'on a bien tenté de décoder
    }

    @Test
    void isValid_shouldReturnFalse_whenDecoderThrowsJwtException() {
        // Arrange
        String token = "bad-token";

        // decoder.decode(...) lève une JwtException => isValid doit renvoyer false
        when(decoder.decode(token))
                .thenThrow(new JwtException("invalid token"));

        // Act
        boolean valid = provider.isValid(token);

        // Assert
        assertFalse(valid, "isValid() doit renvoyer false si decoder.decode() throw JwtException");
        verify(decoder).decode(token);
    }

    @Test
    void extractUserId_shouldReturnUuidFromSubject() {
        // Arrange
        String token = "t";
        UUID expectedUserId = UUID.randomUUID();

        // Le decoder renvoie un Jwt dont le subject = expectedUserId
        when(decoder.decode(token))
                .thenReturn(new Jwt(
                        token,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of("alg", "HS256"),
                        Map.of("sub", expectedUserId.toString(), "tid", UUID.randomUUID().toString())
                ));

        // Act
        UUID userId = provider.extractUserId(token);

        // Assert
        assertEquals(expectedUserId, userId, "extractUserId() doit retourner le UUID présent dans subject");
    }

    @Test
    void extractTenantId_shouldReturnUuidFromTidClaim() {
        // Arrange
        String token = "t";
        UUID expectedTenantId = UUID.randomUUID();

        // Le decoder renvoie un Jwt dont le claim "tid" = expectedTenantId
        when(decoder.decode(token))
                .thenReturn(new Jwt(
                        token,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of("alg", "HS256"),
                        Map.of("sub", UUID.randomUUID().toString(), "tid", expectedTenantId.toString())
                ));

        // Act
        UUID tenantId = provider.extractTenantId(token);

        // Assert
        assertEquals(expectedTenantId, tenantId, "extractTenantId() doit retourner le UUID présent dans claim tid");
    }
}
