package com.yem.hlm.backend.societe.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.media.service.MediaTooLargeException;
import com.yem.hlm.backend.media.service.MediaTypeNotAllowedException;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
public class SocieteLogoService {

    private static final long MAX_LOGO_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );

    private final SocieteRepository societeRepository;
    private final MediaStorageService storageService;

    public SocieteLogoService(SocieteRepository societeRepository, MediaStorageService storageService) {
        this.societeRepository = societeRepository;
        this.storageService = storageService;
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.SOCIETES_CACHE}, allEntries = true)
    public void uploadLogo(UUID societeId, MultipartFile file) throws IOException {
        Societe societe = requireSociete(societeId);
        validateImageFile(file);
        if (societe.getLogoFileKey() != null) {
            storageService.delete(societe.getLogoFileKey());
        }
        String fileKey = storageService.store(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        societe.setLogoFileKey(fileKey);
        societe.setLogoContentType(file.getContentType());
        societeRepository.save(societe);
    }

    public InputStream downloadLogo(UUID societeId) throws IOException {
        Societe societe = requireSociete(societeId);
        if (societe.getLogoFileKey() == null) {
            throw new BusinessRuleException(ErrorCode.SOCIETE_NOT_FOUND,
                    "No logo found for société " + societeId);
        }
        return storageService.load(societe.getLogoFileKey());
    }

    public String getLogoContentType(UUID societeId) {
        return requireSociete(societeId).getLogoContentType();
    }

    public boolean hasLogo(UUID societeId) {
        return societeRepository.findById(societeId)
                .map(s -> s.getLogoFileKey() != null)
                .orElse(false);
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.SOCIETES_CACHE}, allEntries = true)
    public void deleteLogo(UUID societeId) throws IOException {
        Societe societe = requireSociete(societeId);
        if (societe.getLogoFileKey() != null) {
            storageService.delete(societe.getLogoFileKey());
            societe.setLogoFileKey(null);
            societe.setLogoContentType(null);
            societeRepository.save(societe);
        }
    }

    private Societe requireSociete(UUID societeId) {
        return societeRepository.findById(societeId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SOCIETE_NOT_FOUND,
                        "Société not found: " + societeId));
    }

    private void validateImageFile(MultipartFile file) {
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new MediaTooLargeException(file.getSize(), MAX_LOGO_BYTES);
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct)) {
            throw new MediaTypeNotAllowedException(ct);
        }
    }
}
