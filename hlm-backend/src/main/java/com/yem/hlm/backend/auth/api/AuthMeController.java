package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoint simple pour valider que :
 * - JWT est lu
 * - SocieteContext est rempli par le filtre
 */
@Tag(name = "Auth", description = "JWT validation and session introspection")
@RestController
public class AuthMeController {

    private final UserRepository userRepository;
    private final SocieteRepository societeRepository;

    public AuthMeController(UserRepository userRepository, SocieteRepository societeRepository) {
        this.userRepository = userRepository;
        this.societeRepository = societeRepository;
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", SocieteContext.getUserId());
        result.put("societeId", SocieteContext.getSocieteId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .ifPresent(role -> result.put("role", role));
        }

        UUID userId = SocieteContext.getUserId();
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                result.put("langueInterface", user.getLangueInterface());
                result.put("platformRole", user.getPlatformRole());
                result.put("prenom", user.getPrenom());
                result.put("email", user.getEmail());
            });
        }

        UUID societeId = SocieteContext.getSocieteId();
        if (societeId != null) {
            societeRepository.findById(societeId).ifPresent(s -> {
                if (s.getLogoFileKey() != null) {
                    result.put("societeLogoUrl", "/api/societes/" + societeId + "/logo");
                }
            });
        }

        // Impersonation state — non-null imp claim means this is an impersonation session
        UUID impersonatedBy = SocieteContext.getImpersonatedBy();
        if (impersonatedBy != null) {
            result.put("isImpersonating", true);
            userRepository.findById(userId).ifPresent(target ->
                    result.put("impersonationTargetEmail", target.getEmail()));
        } else {
            result.put("isImpersonating", false);
        }

        return result;
    }

    @PutMapping("/auth/me/langue")
    public Map<String, String> updateLangue(@RequestBody Map<String, String> body) {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user context");
        }
        String langue = body.get("langue");
        if (langue == null || !Set.of("fr", "en", "ar").contains(langue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_LANGUAGE");
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setLangueInterface(langue);
            userRepository.save(user);
        });
        return Map.of("langue", langue);
    }
}
