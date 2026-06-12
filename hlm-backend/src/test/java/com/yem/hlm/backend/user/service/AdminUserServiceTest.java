package com.yem.hlm.backend.user.service;

import com.yem.hlm.backend.auth.service.SecurityAuditLogger;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.QuotaService;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.user.api.dto.DeactivateEverywhereResponse;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminUserService#deactivateEverywhere} (finding #004) — Docker-free.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AppUserSocieteRepository appUserSocieteRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserSecurityCacheService userSecurityCacheService;
    @Mock private SecurityAuditLogger securityAuditLogger;
    @Mock private QuotaService quotaService;

    private AdminUserService service;

    private final UUID actorId    = UUID.randomUUID();
    private final UUID targetId   = UUID.randomUUID();
    private final UUID societeA   = UUID.randomUUID();
    private final UUID societeB   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, appUserSocieteRepository, passwordEncoder,
                userSecurityCacheService, securityAuditLogger, quotaService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AppUserSociete membership(UUID userId, UUID societeId, String role, boolean actif) {
        AppUserSociete m = new AppUserSociete(new AppUserSocieteId(userId, societeId), role);
        m.setActif(actif);
        return m;
    }

    @Test
    @DisplayName("Deactivates every active membership, disables account, bumps token, audits per société")
    void deactivateEverywhere_happyPath() {
        AppUserSociete a = membership(targetId, societeA, "AGENT", true);
        AppUserSociete b = membership(targetId, societeB, "MANAGER", true);
        when(appUserSocieteRepository.findByIdUserId(targetId)).thenReturn(List.of(a, b));
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(actorId))
                .thenReturn(List.of(membership(actorId, societeA, "ADMIN", true)));
        User target = mock(User.class);
        when(target.getEmail()).thenReturn("agent@atlas.ma");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(mock(User.class)));

        DeactivateEverywhereResponse res = service.deactivateEverywhere(targetId, null);

        assertThat(res.societesDesactivees()).isEqualTo(2);
        assertThat(res.compteDesactive()).isTrue();
        assertThat(a.isActif()).isFalse();
        assertThat(b.isActif()).isFalse();
        verify(target).setEnabled(false);
        verify(target).incrementTokenVersion();
        verify(userSecurityCacheService).evict(targetId);
        verify(securityAuditLogger, times(2))
                .logTokenRevocation(org.mockito.ArgumentMatchers.anyString(), eq(targetId), eq(actorId), eq("OFFBOARDED_ALL"));
    }

    @Test
    @DisplayName("403 when the actor shares no ADMIN société with the target")
    void deactivateEverywhere_noSharedAdminSociete() {
        when(appUserSocieteRepository.findByIdUserId(targetId))
                .thenReturn(List.of(membership(targetId, societeB, "AGENT", true)));
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(actorId))
                .thenReturn(List.of(membership(actorId, societeA, "ADMIN", true))); // different société

        assertThatThrownBy(() -> service.deactivateEverywhere(targetId, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("403 when the actor is only MANAGER (not ADMIN) in the shared société")
    void deactivateEverywhere_actorNotAdmin() {
        when(appUserSocieteRepository.findByIdUserId(targetId))
                .thenReturn(List.of(membership(targetId, societeA, "AGENT", true)));
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(actorId))
                .thenReturn(List.of(membership(actorId, societeA, "MANAGER", true)));

        assertThatThrownBy(() -> service.deactivateEverywhere(targetId, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("403 when an admin tries to off-board themselves")
    void deactivateEverywhere_selfBlocked() {
        assertThatThrownBy(() -> service.deactivateEverywhere(actorId, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(appUserSocieteRepository, never()).findByIdUserId(actorId);
    }

    @Test
    @DisplayName("404 when the target user has no memberships")
    void deactivateEverywhere_unknownUser() {
        when(appUserSocieteRepository.findByIdUserId(targetId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.deactivateEverywhere(targetId, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Already-inactive memberships are not re-counted")
    void deactivateEverywhere_skipsAlreadyInactive() {
        AppUserSociete active   = membership(targetId, societeA, "AGENT", true);
        AppUserSociete inactive = membership(targetId, societeB, "AGENT", false);
        when(appUserSocieteRepository.findByIdUserId(targetId)).thenReturn(List.of(active, inactive));
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(actorId))
                .thenReturn(List.of(membership(actorId, societeA, "ADMIN", true)));
        User target = mock(User.class);
        lenient().when(target.getEmail()).thenReturn("agent@atlas.ma");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(mock(User.class)));

        DeactivateEverywhereResponse res = service.deactivateEverywhere(targetId, null);

        assertThat(res.societesDesactivees()).isEqualTo(1);
        verify(securityAuditLogger, times(1))
                .logTokenRevocation(org.mockito.ArgumentMatchers.anyString(), eq(targetId), eq(actorId), eq("OFFBOARDED_ALL"));
    }
}
