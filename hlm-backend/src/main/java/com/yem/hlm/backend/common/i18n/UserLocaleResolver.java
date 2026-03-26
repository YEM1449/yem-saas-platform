package com.yem.hlm.backend.common.i18n;

import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.UUID;

/**
 * Resolves locale in priority order:
 * 1. Accept-Language header (explicit user preference per request)
 * 2. User.langueInterface (persisted user preference)
 * 3. French (system default)
 */
@Component
public class UserLocaleResolver implements LocaleResolver {

    private static final Locale DEFAULT_LOCALE = Locale.FRENCH;
    private final UserRepository userRepository;

    public UserLocaleResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 1. Accept-Language header
        String acceptLang = request.getHeader("Accept-Language");
        if (acceptLang != null && !acceptLang.isBlank()) {
            String lang = acceptLang.split("[,;]")[0].trim();
            if (lang.length() >= 2) {
                String code = lang.substring(0, 2).toLowerCase();
                if (isSupported(code)) return Locale.forLanguageTag(code);
            }
        }

        // 2. User preference from DB
        UUID userId = SocieteContext.getUserId();
        if (userId != null) {
            String userLang = userRepository.findById(userId)
                    .map(u -> u.getLangueInterface())
                    .orElse(null);
            if (userLang != null && isSupported(userLang)) return Locale.forLanguageTag(userLang);
        }

        return DEFAULT_LOCALE;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // No-op: locale is derived from user preference, not cookie/session
    }

    private boolean isSupported(String lang) {
        return "fr".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang) || "ar".equalsIgnoreCase(lang);
    }
}
