package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.usermanagement.dto.*;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock AppUserSocieteRepository appUserSocieteRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks UserManagementService service;

    private UUID societeId;
    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        societeId = UUID.randomUUID();
        adminId   = UUID.randomUUID();
        userId    = UUID.randomUUID();
    }

    private static User userWithId(UUID id) {
        User u = new User("user@test.com", "hash");
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    private AppUserSociete aus(UUID userId, UUID societeId, String role) {
        AppUserSociete a = new AppUserSociete(new AppUserSocieteId(userId, societeId), role);
        a.setActif(true);
        return a;
    }

    // ── modifierProfil ─────────────────────────────────────────────────────────

    @Test
    void test_modifierProfil_versionOk_metAJourEtPublieEvent() {
        User user = userWithId(userId);
        ModifierUtilisateurRequest req = new ModifierUtilisateurRequest(
                "Alice", null, null, null, null, null, null, 0L);

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));

        MembreDto result = service.modifierProfil(userId, societeId, req, adminId);

        assertThat(result).isNotNull();
        assertThat(user.getPrenom()).isEqualTo("Alice");
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void test_modifierProfil_versionKo_retourne409() {
        User user = userWithId(userId);
        // User has version 0, but request sends version 99
        ModifierUtilisateurRequest req = new ModifierUtilisateurRequest(
                "Alice", null, null, null, null, null, null, 99L);

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.modifierProfil(userId, societeId, req, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(CONCURRENT_UPDATE);
    }

    // ── changerRole ────────────────────────────────────────────────────────────

    @Test
    void test_changerRole_adminVersAgent_quandPlusieursAdmins_ok() {
        User user = userWithId(userId);
        ChangerRoleRequest req = new ChangerRoleRequest("AGENT", "reassignment", 0L);

        AppUserSociete ausObj = aus(userId, societeId, "ADMIN");
        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(ausObj));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // First call returns ADMIN (to get old role), second call (in toMembreDto) returns same obj
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(ausObj));
        when(appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN")).thenReturn(2L);
        when(userRepository.save(any())).thenReturn(user);

        MembreDto result = service.changerRole(userId, societeId, req, adminId);

        assertThat(result).isNotNull();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void test_changerRole_dernierAdmin_retourne409() {
        User user = userWithId(userId);
        ChangerRoleRequest req = new ChangerRoleRequest("AGENT", "reassignment", 0L);

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "ADMIN")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "ADMIN")));
        when(appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.changerRole(userId, societeId, req, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(DERNIER_ADMIN);
    }

    // ── retirerMembre ──────────────────────────────────────────────────────────

    @Test
    void test_retirerMembre_dernierAdmin_retourne409() {
        User user = userWithId(userId);
        RetirerUtilisateurRequest req = new RetirerUtilisateurRequest("Leaving", 0L);

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "ADMIN")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "ADMIN")));
        when(appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.retirerMembre(userId, societeId, req, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(DERNIER_ADMIN);
    }

    @Test
    void test_retirerMembre_agent_ok_invalidatesJwt() {
        User user = userWithId(userId);
        int originalTokenVersion = user.getTokenVersion();
        RetirerUtilisateurRequest req = new RetirerUtilisateurRequest("Left the company", 0L);

        AppUserSociete ausObj = aus(userId, societeId, "AGENT");
        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(ausObj));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(ausObj));
        when(userRepository.getReferenceById(adminId)).thenReturn(userWithId(adminId));
        when(userRepository.save(any())).thenReturn(user);

        service.retirerMembre(userId, societeId, req, adminId);

        assertThat(user.getTokenVersion()).isEqualTo(originalTokenVersion + 1);
        assertThat(ausObj.isActif()).isFalse();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    // ── debloquerCompte ────────────────────────────────────────────────────────

    @Test
    void test_debloquerCompte_ok() {
        User user = userWithId(userId);
        user.setCompteBloque(true);

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));

        service.debloquerCompte(userId, societeId, adminId);

        assertThat(user.isCompteBloque()).isFalse();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void test_debloquerCompte_dejaDebloque_retourne400() {
        User user = userWithId(userId); // compteBloque = false by default

        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.of(aus(userId, societeId, "AGENT")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.debloquerCompte(userId, societeId, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(COMPTE_DEJA_DEBLOQUE);
    }

    // ── findMembre isolation ───────────────────────────────────────────────────

    @Test
    void test_findMembre_nonMembre_retourne404() {
        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMembre(userId, societeId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(MEMBRE_NON_TROUVE);
    }
}
