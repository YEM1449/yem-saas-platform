package com.yem.hlm.backend.gdpr.api;

import com.yem.hlm.backend.gdpr.api.GdprController.PrivacyNoticeResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the privacy notice text from the classpath resource {@code gdpr/privacy-notice.txt}.
 * The text is cached at startup. If the file is absent, a built-in French default is used.
 */
@Component
public class PrivacyNoticeLoader {

    private static final Logger log = LoggerFactory.getLogger(PrivacyNoticeLoader.class);

    private static final String VERSION = "1.0";
    private static final String LAST_UPDATED = "2026-03";

    private static final String DEFAULT_NOTICE =
            "Dans le cadre de la gestion de vos contacts immobiliers, [NOM DE LA SOCIÉTÉ] " +
            "collecte et traite vos données personnelles (nom, prénom, email, téléphone) " +
            "sur la base de votre consentement ou de l'exécution d'un contrat. " +
            "Vos données sont conservées pendant une durée maximale de 5 ans à compter de " +
            "votre dernière interaction. Conformément au RGPD (UE 2016/679) et à la Loi 09-08 " +
            "(Maroc), vous disposez d'un droit d'accès, de rectification, d'effacement et de " +
            "portabilité. Pour exercer ces droits, contactez [EMAIL DPO].";

    private String noticeText;

    @PostConstruct
    void init() {
        ClassPathResource resource = new ClassPathResource("gdpr/privacy-notice.txt");
        if (resource.exists()) {
            try {
                noticeText = resource.getContentAsString(StandardCharsets.UTF_8);
                log.info("[GDPR] Privacy notice loaded from classpath:gdpr/privacy-notice.txt");
            } catch (IOException e) {
                log.warn("[GDPR] Failed to read privacy notice file, using built-in default", e);
                noticeText = DEFAULT_NOTICE;
            }
        } else {
            log.info("[GDPR] No privacy-notice.txt found — using built-in French default");
            noticeText = DEFAULT_NOTICE;
        }
    }

    public PrivacyNoticeResponse load() {
        return new PrivacyNoticeResponse(VERSION, LAST_UPDATED, noticeText);
    }
}
