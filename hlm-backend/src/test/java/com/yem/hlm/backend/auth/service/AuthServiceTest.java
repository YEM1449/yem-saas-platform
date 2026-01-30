package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.service.AuthService;
import com.yem.hlm.backend.auth.service.UnauthorizedException;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests UNITAIRES du AuthService.
 *
 * Contraintes respectées :
 * - ❌ pas de setters
 * - ❌ pas de UUID dans les constructeurs
 * - ❌ pas de boolean enabled dans les constructeurs
 * - ✅ simulation du comportement JPA via spies
 * - ✅ domaine non modifié
 */
class AuthServiceTest {

    // Dépendances du service (mockées)
    private TenantRepository tenantRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    // Service réel testé
    private AuthService authService;

    /**
     * Initialisation avant chaque test.
     * On recrée un environnement propre et isolé.
     */
    @BeforeEach
    void setup() {
        tenantRepository = mock(TenantRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        authService = new AuthService(
                tenantRepository,
                userRepository,
                passwordEncoder
        );
    }

    /**
     * CAS NOMINAL
     *
     * - tenant existe
     * - user existe dans le tenant
     * - user activé
     * - mot de passe correct
     *
     * => login réussi
     */
    @Test
    void login_success() {

        // --- Arrange ---

        // UUID simulé comme généré par JPA
        UUID tenantId = UUID.randomUUID();

        // Création du tenant via le constructeur réel
        Tenant tenant = new Tenant(
                "acme",
                "ACME Immobilier"
        );

        // Spy pour simuler l'ID généré par JPA
        Tenant tenantSpy = spy(tenant);
        when(tenantSpy.getId()).thenReturn(tenantId);

        // Création du user via le constructeur réel
        User user = new User(
                tenantSpy,
                "admin@acme.com",
                "HASHED_PASSWORD"
        );

        // Spy pour simuler :
        // - enabled = true (champ géré par JPA)
        User userSpy = spy(user);
        when(userSpy.isEnabled()).thenReturn(true);

        // Requête de login simulée
        LoginRequest request = new LoginRequest(
                "acme",
                "admin@acme.com",
                "admin123!"
        );

        // Simulation du repository tenant
        when(tenantRepository.findByKey("acme"))
                .thenReturn(Optional.of(tenantSpy));

        // Simulation du repository user
        when(userRepository.findByTenant_IdAndEmail(
                tenantId,
                "admin@acme.com"
        )).thenReturn(Optional.of(userSpy));

        // Simulation BCrypt : mot de passe correct
        when(passwordEncoder.matches("admin123!", "HASHED_PASSWORD"))
                .thenReturn(true);

        // --- Act ---
        var response = authService.login(request);

        // --- Assert ---
        assertNotNull(response);
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
    }

    /**
     * CAS ERREUR
     *
     * - user désactivé
     *
     * => UnauthorizedException
     */
    @Test
    void login_fails_when_user_is_disabled() {

        UUID tenantId = UUID.randomUUID();

        Tenant tenant = new Tenant(
                "acme",
                "ACME Immobilier"
        );

        Tenant tenantSpy = spy(tenant);
        when(tenantSpy.getId()).thenReturn(tenantId);

        User user = new User(
                tenantSpy,
                "admin@acme.com",
                "HASHED_PASSWORD"
        );

        User userSpy = spy(user);
        when(userSpy.isEnabled()).thenReturn(false);

        LoginRequest request = new LoginRequest(
                "acme",
                "admin@acme.com",
                "admin123!"
        );

        when(tenantRepository.findByKey("acme"))
                .thenReturn(Optional.of(tenantSpy));

        when(userRepository.findByTenant_IdAndEmail(
                tenantId,
                "admin@acme.com"
        )).thenReturn(Optional.of(userSpy));

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );
    }

    /**
     * CAS ERREUR
     *
     * - tenant inexistant
     *
     * => UnauthorizedException
     */
    @Test
    void login_fails_when_tenant_not_found() {

        LoginRequest request = new LoginRequest(
                "acme",
                "admin@acme.com",
                "admin123!"
        );

        when(tenantRepository.findByKey("acme"))
                .thenReturn(Optional.empty());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );
    }
}
