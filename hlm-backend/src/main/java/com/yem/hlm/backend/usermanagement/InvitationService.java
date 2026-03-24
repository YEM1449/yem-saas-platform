package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.common.security.SocieteRoleValidator;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.QuotaService;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.dto.ActivationRequest;
import com.yem.hlm.backend.usermanagement.dto.InvitationDetailsDto;
import com.yem.hlm.backend.usermanagement.dto.InviterUtilisateurRequest;
import com.yem.hlm.backend.usermanagement.event.UserActivatedEvent;
import com.yem.hlm.backend.usermanagement.event.UserInvitedEvent;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.*;

@Service
@Transactional
public class InvitationService {

    private static final int EXPIRATION_HEURES = 72;

    private final UserRepository userRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final SocieteRepository societeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final SocieteRoleValidator roleValidator;
    private final QuotaService quotaService;

    public InvitationService(UserRepository userRepository,
                             AppUserSocieteRepository appUserSocieteRepository,
                             SocieteRepository societeRepository,
                             PasswordEncoder passwordEncoder,
                             JwtProvider jwtProvider,
                             JwtProperties jwtProperties,
                             ApplicationEventPublisher eventPublisher,
                             SocieteRoleValidator roleValidator,
                             QuotaService quotaService) {
        this.userRepository = userRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.societeRepository = societeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.eventPublisher = eventPublisher;
        this.roleValidator = roleValidator;
        this.quotaService = quotaService;
    }

    // ── inviter ────────────────────────────────────────────────────────────────

    public User inviter(InviterUtilisateurRequest req, UUID societeId, UUID adminId) {
        // Defense-in-depth: re-validate role even when called programmatically
        roleValidator.validateAssignableRole(req.role());

        Societe societe = societeRepository.findById(societeId)
                .orElseThrow(() -> new BusinessRuleException(SOCIETE_NOT_FOUND,
                        "Société introuvable : " + societeId));

        // 2. Chercher ou créer app_user
        String normalizedEmail = req.email().toLowerCase().trim();
        Optional<User> existingOpt = userRepository.findByEmail(normalizedEmail);
        boolean isExistingUser = existingOpt.isPresent();

        User user;
        if (isExistingUser) {
            user = existingOpt.get();
            // 3. Already active member?
            boolean dejaActif = appUserSocieteRepository
                    .findByUserIdAndSocieteIdAndActifTrue(user.getId(), societeId).isPresent();
            if (dejaActif) throw new BusinessRuleException(MEMBRE_DEJA_EXISTANT,
                    "Cet utilisateur est déjà membre actif de cette société.");
            // 4. Pending invitation?
            if (!user.isEnabled()
                    && user.getInvitationToken() != null
                    && user.getInvitationExpireAt() != null
                    && user.getInvitationExpireAt().isAfter(Instant.now())) {
                throw new BusinessRuleException(INVITATION_EN_COURS,
                        "Une invitation valide existe déjà pour cet email.");
            }
        } else {
            // Placeholder hash — replaced on activation
            user = new User(normalizedEmail, passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEnabled(false);
        }

        // 5. Quota guard — only count as a new slot if this is a new/reactivated membership
        quotaService.enforceUserQuota(societeId);

        // 6. Set profile & generate token
        user.setPrenom(req.prenom());
        user.setNomFamille(req.nomFamille());
        user.setTelephone(req.telephone());
        user.setPoste(req.poste());
        user.setLangueInterface(req.langueInterface());
        user.setInvitationToken(generateSecureToken());
        user.setInvitationExpireAt(Instant.now().plus(EXPIRATION_HEURES, ChronoUnit.HOURS));
        user.setInvitationEnvoyeeAt(Instant.now());

        User adminRef = userRepository.getReferenceById(adminId);
        user.setInvitePar(adminRef);
        final User savedUser = userRepository.save(user);

        // 6. Create or reactivate app_user_societe
        AppUserSociete aus = appUserSocieteRepository
                .findByUserIdAndSocieteId(savedUser.getId(), societeId)
                .orElseGet(() -> new AppUserSociete(
                        new AppUserSocieteId(savedUser.getId(), societeId), req.role()));
        aus.setRole(req.role());
        aus.setActif(true);
        aus.setDateAjout(Instant.now());
        aus.setAjoutePar(adminRef);
        appUserSocieteRepository.save(aus);

        // 7. Publish domain event (→ audit + email via listener)
        eventPublisher.publishEvent(new UserInvitedEvent(
                savedUser.getId(), societeId, adminId, req.role(), req.messagePersonnalise()));

        return savedUser;
    }

    // ── reinviter ──────────────────────────────────────────────────────────────

    public User reinviter(UUID userId, UUID societeId, UUID adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE,
                        "Utilisateur introuvable : " + userId));

        // Reset invitation
        user.setInvitationToken(generateSecureToken());
        user.setInvitationExpireAt(Instant.now().plus(EXPIRATION_HEURES, ChronoUnit.HOURS));
        user.setInvitationEnvoyeeAt(Instant.now());
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(new UserInvitedEvent(
                saved.getId(), societeId, adminId, null, null));
        return saved;
    }

    // ── validateToken ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InvitationDetailsDto validateToken(String token) {
        User user = userRepository.findByInvitationToken(token)
                .orElseThrow(() -> new BusinessRuleException(INVITATION_EXPIREE,
                        "Lien d'invitation invalide ou déjà utilisé."));

        if (user.getInvitationExpireAt() == null
                || user.getInvitationExpireAt().isBefore(Instant.now())) {
            throw new BusinessRuleException(INVITATION_EXPIREE,
                    "Ce lien d'invitation a expiré. Demandez à votre administrateur d'en envoyer un nouveau.");
        }

        AppUserSociete aus = appUserSocieteRepository.findByIdUserIdAndActifTrue(user.getId())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException(INVITATION_EXPIREE, "Invitation introuvable."));

        Societe societe = societeRepository.findById(aus.getSocieteId()).orElseThrow();

        long minutesRestantes = Instant.now().until(user.getInvitationExpireAt(), ChronoUnit.MINUTES);
        String expireDans = minutesRestantes > 60
                ? (minutesRestantes / 60) + "h " + (minutesRestantes % 60) + "m"
                : minutesRestantes + " minutes";

        return new InvitationDetailsDto(user.getPrenom(), user.getEmail(),
                societe.getNom(), aus.getRole(), expireDans);
    }

    // ── activerCompte ──────────────────────────────────────────────────────────

    public LoginResponse activerCompte(String token, ActivationRequest req) {
        User user = userRepository.findByInvitationToken(token)
                .orElseThrow(() -> new BusinessRuleException(INVITATION_EXPIREE, "Lien invalide."));

        if (user.getInvitationExpireAt() == null
                || user.getInvitationExpireAt().isBefore(Instant.now())) {
            throw new BusinessRuleException(INVITATION_EXPIREE, "Lien expiré.");
        }

        validatePasswordStrength(req.motDePasse(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(req.motDePasse()));
        user.setEnabled(true);
        user.setConsentementCgu(true);
        user.setConsentementCguDate(Instant.now());
        user.setConsentementCguVersion(req.consentementCguVersion());
        user.setInvitationToken(null);      // C8: token à usage unique
        user.setInvitationExpireAt(null);
        userRepository.save(user);

        AppUserSociete aus = appUserSocieteRepository
                .findByIdUserIdAndActifTrue(user.getId()).stream().findFirst().orElseThrow();

        eventPublisher.publishEvent(new UserActivatedEvent(
                user.getId(), aus.getSocieteId(), req.consentementCguVersion()));

        String role = "SUPER_ADMIN".equals(user.getPlatformRole())
                ? "ROLE_SUPER_ADMIN" : "ROLE_" + aus.getRole();
        String jwt = jwtProvider.generate(user.getId(), aus.getSocieteId(), role, user.getTokenVersion());
        return LoginResponse.bearer(jwt, jwtProperties.ttlSeconds());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private void validatePasswordStrength(String pwd, String email) {
        if (pwd.length() < 12)
            throw new BusinessRuleException(MOT_DE_PASSE_TROP_COURT, "Minimum 12 caractères.");
        if (!pwd.matches(".*[A-Z].*"))
            throw new BusinessRuleException(MOT_DE_PASSE_TROP_FAIBLE, "Au moins une majuscule requise.");
        if (!pwd.matches(".*[a-z].*"))
            throw new BusinessRuleException(MOT_DE_PASSE_TROP_FAIBLE, "Au moins une minuscule requise.");
        if (!pwd.matches(".*[0-9].*"))
            throw new BusinessRuleException(MOT_DE_PASSE_TROP_FAIBLE, "Au moins un chiffre requis.");
        if (!pwd.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*"))
            throw new BusinessRuleException(MOT_DE_PASSE_TROP_FAIBLE, "Au moins un caractère spécial requis.");
        if (email != null && pwd.toLowerCase().contains(email.split("@")[0].toLowerCase()))
            throw new BusinessRuleException(MOT_DE_PASSE_CONTIENT_EMAIL,
                    "Le mot de passe ne doit pas contenir l'adresse email.");
    }
}
