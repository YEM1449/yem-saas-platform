package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.common.security.SocieteRoleValidator;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.dto.ActivationRequest;
import com.yem.hlm.backend.usermanagement.dto.InvitationDetailsDto;
import com.yem.hlm.backend.usermanagement.dto.InviterUtilisateurRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock UserRepository userRepository;
    @Mock AppUserSocieteRepository appUserSocieteRepository;
    @Mock SocieteRepository societeRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock JwtProperties jwtProperties;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock SocieteRoleValidator roleValidator;

    @InjectMocks InvitationService invitationService;

    private UUID societeId;
    private UUID adminId;
    private Societe societe;

    @BeforeEach
    void setUp() {
        societeId = UUID.randomUUID();
        adminId   = UUID.randomUUID();
        societe   = new Societe("Test Société", "MA");
    }

    /** Sets the private id field of a User via reflection. */
    private static User withId(User user) {
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    // ── inviter ────────────────────────────────────────────────────────────────

    @Test
    void test_inviter_nouveauUtilisateur_creeLEnregistrementEtPublieEvent() {
        InviterUtilisateurRequest req = new InviterUtilisateurRequest(
                "alice@example.com", "Alice", "Dupont",
                null, null, "AGENT", null, null);

        when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");

        User savedUser = withId(new User("alice@example.com", "$2a$hash"));
        when(userRepository.getReferenceById(adminId)).thenReturn(withId(new User("admin@example.com", "h")));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(appUserSocieteRepository.findByUserIdAndSocieteId(any(), eq(societeId)))
                .thenReturn(Optional.empty());

        User result = invitationService.inviter(req, societeId, adminId);

        assertThat(result).isNotNull();
        verify(appUserSocieteRepository).save(any(AppUserSociete.class));
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void test_inviter_membreDejaActif_retourne409() {
        InviterUtilisateurRequest req = new InviterUtilisateurRequest(
                "bob@example.com", "Bob", "Martin",
                null, null, "AGENT", null, null);

        User existingUser = withId(new User("bob@example.com", "hash"));
        when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existingUser));

        AppUserSociete aus = new AppUserSociete(
                new AppUserSocieteId(existingUser.getId(), societeId), "AGENT");
        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(existingUser.getId(), societeId))
                .thenReturn(Optional.of(aus));

        assertThatThrownBy(() -> invitationService.inviter(req, societeId, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(MEMBRE_DEJA_EXISTANT);
    }

    @Test
    void test_inviter_invitationDejaEnCours_retourne409() {
        InviterUtilisateurRequest req = new InviterUtilisateurRequest(
                "charlie@example.com", "Charlie", "Durand",
                null, null, "MANAGER", null, null);

        User existingUser = withId(new User("charlie@example.com", "hash"));
        existingUser.setEnabled(false);   // user has never activated (pending invitation)
        existingUser.setInvitationToken("existingtoken");
        existingUser.setInvitationExpireAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(existingUser));
        when(appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(existingUser.getId(), societeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.inviter(req, societeId, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(INVITATION_EN_COURS);
    }

    // ── validateToken ──────────────────────────────────────────────────────────

    @Test
    void test_validateToken_tokenValide_retourneDetails() {
        String token = "validtoken123";
        User user = withId(new User("diana@example.com", "hash"));
        user.setPrenom("Diana");
        user.setInvitationExpireAt(Instant.now().plus(48, ChronoUnit.HOURS));

        AppUserSociete aus = new AppUserSociete(
                new AppUserSocieteId(user.getId(), societeId), "MANAGER");

        when(userRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(user.getId())).thenReturn(List.of(aus));
        when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));

        InvitationDetailsDto dto = invitationService.validateToken(token);

        assertThat(dto.email()).isEqualTo("diana@example.com");
        assertThat(dto.prenom()).isEqualTo("Diana");
        assertThat(dto.societeNom()).isEqualTo("Test Société");
        assertThat(dto.role()).isEqualTo("MANAGER");
    }

    @Test
    void test_validateToken_tokenExpire_retourneErreur() {
        String token = "expiredtoken";
        User user = withId(new User("expired@example.com", "hash"));
        user.setInvitationExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(userRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> invitationService.validateToken(token))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(INVITATION_EXPIREE);
    }

    @Test
    void test_validateToken_tokenInconnu_retourneErreur() {
        when(userRepository.findByInvitationToken("unknowntoken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.validateToken("unknowntoken"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(INVITATION_EXPIREE);
    }

    // ── activerCompte ──────────────────────────────────────────────────────────

    @Test
    void test_activerCompte_motDePasseValide_retourneJWT() {
        String token = "activatetoken";
        User user = withId(new User("eve@example.com", "oldhash"));
        user.setInvitationExpireAt(Instant.now().plus(24, ChronoUnit.HOURS));

        ActivationRequest req = new ActivationRequest(
                "Admin123!Secure", "Admin123!Secure", true, "v1.0");

        AppUserSociete aus = new AppUserSociete(
                new AppUserSocieteId(user.getId(), societeId), "AGENT");

        when(userRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Admin123!Secure")).thenReturn("$2a$newhash");
        when(userRepository.save(any())).thenReturn(user);
        when(appUserSocieteRepository.findByIdUserIdAndActifTrue(user.getId())).thenReturn(List.of(aus));
        when(jwtProvider.generate(any(UUID.class), any(UUID.class), anyString(), anyInt()))
                .thenReturn("jwt.token.here");
        when(jwtProperties.ttlSeconds()).thenReturn(3600L);

        LoginResponse response = invitationService.activerCompte(token, req);

        assertThat(response.accessToken()).isEqualTo("jwt.token.here");
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void test_activerCompte_motDePasseFaible_retourne400() {
        String token = "activatetoken";
        User user = withId(new User("frank@example.com", "hash"));
        user.setInvitationExpireAt(Instant.now().plus(24, ChronoUnit.HOURS));

        ActivationRequest req = new ActivationRequest(
                "weak", "weak", true, "v1.0");

        when(userRepository.findByInvitationToken(token)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> invitationService.activerCompte(token, req))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(MOT_DE_PASSE_TROP_COURT);
    }
}
