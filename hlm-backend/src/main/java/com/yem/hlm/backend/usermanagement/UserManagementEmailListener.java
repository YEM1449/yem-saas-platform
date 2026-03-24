package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.usermanagement.event.UserActivatedEvent;
import com.yem.hlm.backend.usermanagement.event.UserInvitedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends transactional emails in response to user management domain events.
 * Runs asynchronously so email delivery latency does not affect the request thread.
 */
@Component
public class UserManagementEmailListener {

    private static final Logger log = LoggerFactory.getLogger(UserManagementEmailListener.class);

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public UserManagementEmailListener(UserRepository userRepository,
                                       EmailSender emailSender) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    // ── Invitation email ───────────────────────────────────────────────────────

    @EventListener
    @Async
    public void onUserInvited(UserInvitedEvent event) {
        userRepository.findById(event.userId).ifPresentOrElse(user -> {
            if (user.getInvitationToken() == null) {
                log.warn("[EMAIL] Invitation token missing for userId={}", event.userId);
                return;
            }
            String link = frontendBaseUrl + "/activation?token=" + user.getInvitationToken();
            String subject = "Votre invitation sur la plateforme YEM";
            String body = buildInvitationBody(user, link, event.messagePersonnalise);
            sendSafely(user.getEmail(), subject, body);
        }, () -> log.warn("[EMAIL] User not found for invitation email userId={}", event.userId));
    }

    // ── Activation confirmation email ──────────────────────────────────────────

    @EventListener
    @Async
    public void onUserActivated(UserActivatedEvent event) {
        userRepository.findById(event.userId).ifPresent(user -> {
            String subject = "Votre compte YEM est activé";
            String body = buildActivationConfirmBody(user);
            sendSafely(user.getEmail(), subject, body);
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void sendSafely(String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
        } catch (Exception e) {
            log.error("[EMAIL] Failed to send '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    private String buildInvitationBody(User user, String link, String messagePersonnalise) {
        String prenom = user.getPrenom() != null ? user.getPrenom() : user.getEmail();
        StringBuilder sb = new StringBuilder();
        sb.append("Bonjour ").append(prenom).append(",\n\n");
        if (messagePersonnalise != null && !messagePersonnalise.isBlank()) {
            sb.append(messagePersonnalise).append("\n\n");
        }
        sb.append("Vous avez été invité à rejoindre la plateforme YEM.\n\n");
        sb.append("Cliquez sur le lien ci-dessous pour créer votre mot de passe et activer votre compte :\n");
        sb.append(link).append("\n\n");
        sb.append("Ce lien est valide 72 heures.\n\n");
        sb.append("Cordialement,\nL'équipe YEM");
        return sb.toString();
    }

    private String buildActivationConfirmBody(User user) {
        String prenom = user.getPrenom() != null ? user.getPrenom() : user.getEmail();
        return "Bonjour " + prenom + ",\n\n"
                + "Votre compte YEM a bien été activé. Vous pouvez maintenant vous connecter.\n\n"
                + frontendBaseUrl + "/login\n\n"
                + "Cordialement,\nL'équipe YEM";
    }
}
