package com.yem.hlm.backend.common.i18n;

import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class I18nConfig {

    /**
     * Registers UserLocaleResolver under the Spring MVC canonical bean name
     * "localeResolver" so that DispatcherServlet uses it automatically and
     * LocaleContextHolder is populated for every request.
     */
    @Bean("localeResolver")
    LocaleResolver localeResolver(UserRepository userRepository) {
        return new UserLocaleResolver(userRepository);
    }
}
