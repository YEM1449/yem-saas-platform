package com.yem.hlm.backend.admin;

import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bootstraps the first SUPER_ADMIN account on first deploy.
 *
 * <p>Activated only when {@code app.bootstrap.enabled=true} is set in the environment.
 * R8: Idempotent — running it twice is safe.
 *
 * <p>Usage:
 * <pre>
 * APP_BOOTSTRAP_ENABLED=true \
 * APP_BOOTSTRAP_EMAIL=superadmin@yem.ma \
 * APP_BOOTSTRAP_PASSWORD=VerySecure2026! \
 * ./mvnw spring-boot:run
 * </pre>
 *
 * After the first successful start, set {@code APP_BOOTSTRAP_ENABLED=false}
 * and remove the credentials from the environment.
 */
@Service
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class SuperAdminBootstrapService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;

    public SuperAdminBootstrapService(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      Environment env) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String email    = env.getProperty("app.bootstrap.superadmin.email");
        String password = env.getProperty("app.bootstrap.superadmin.password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("[BOOTSTRAP] Skipped: app.bootstrap.superadmin.email or .password not set.");
            return;
        }

        String normalizedEmail = email.toLowerCase().trim();

        // R8: idempotent — if already a SUPER_ADMIN, skip
        var existing = userRepository.findFirstByEmail(normalizedEmail);
        if (existing.isPresent() && "SUPER_ADMIN".equals(existing.get().getPlatformRole())) {
            log.info("[BOOTSTRAP] Skipped: {} is already a SUPER_ADMIN.", normalizedEmail);
            return;
        }

        validatePasswordStrength(password, normalizedEmail);

        User user = existing.orElseGet(() -> new User(normalizedEmail,
                passwordEncoder.encode(UUID_PLACEHOLDER)));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setPlatformRole("SUPER_ADMIN");
        user.setConsentementCgu(true);
        user.setConsentementCguDate(Instant.now());
        user.setConsentementCguVersion("BOOTSTRAP");
        userRepository.save(user);

        // NEVER log the password
        log.info("[BOOTSTRAP] SUPER_ADMIN bootstrapped successfully for email: {}", normalizedEmail);
    }

    private void validatePasswordStrength(String pwd, String email) {
        if (pwd.length() < 12)
            throw new IllegalArgumentException("[BOOTSTRAP] Password too short — minimum 12 characters.");
        if (!pwd.matches(".*[A-Z].*"))
            throw new IllegalArgumentException("[BOOTSTRAP] Password must contain at least one uppercase letter.");
        if (!pwd.matches(".*[a-z].*"))
            throw new IllegalArgumentException("[BOOTSTRAP] Password must contain at least one lowercase letter.");
        if (!pwd.matches(".*[0-9].*"))
            throw new IllegalArgumentException("[BOOTSTRAP] Password must contain at least one digit.");
        if (!pwd.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*"))
            throw new IllegalArgumentException("[BOOTSTRAP] Password must contain at least one special character.");
        String localPart = email.split("@")[0].toLowerCase();
        if (!localPart.isBlank() && pwd.toLowerCase().contains(localPart))
            throw new IllegalArgumentException("[BOOTSTRAP] Password must not contain the email address.");
    }

    // Placeholder to satisfy the User constructor — replaced immediately
    private static final String UUID_PLACEHOLDER = java.util.UUID.randomUUID().toString();
}
