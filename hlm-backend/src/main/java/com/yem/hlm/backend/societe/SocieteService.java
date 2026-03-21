package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.societe.api.dto.*;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SocieteService {

    private final SocieteRepository societeRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final UserRepository userRepository;

    public SocieteService(SocieteRepository societeRepository,
                          AppUserSocieteRepository appUserSocieteRepository,
                          UserRepository userRepository) {
        this.societeRepository = societeRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.SOCIETES_CACHE)
    public List<SocieteDto> listSocietes() {
        return societeRepository.findAll().stream().map(SocieteDto::from).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SOCIETES_CACHE, key = "#id")
    public SocieteDto getSociete(UUID id) {
        return SocieteDto.from(require(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public SocieteDto createSociete(CreateSocieteRequest req) {
        Societe s = new Societe(req.nom(), req.pays() != null ? req.pays() : "MA");
        s.setSiretIce(req.siretIce());
        s.setAdresse(req.adresse());
        s.setEmailDpo(req.emailDpo());
        s.setLogoUrl(req.logoUrl());
        return SocieteDto.from(societeRepository.save(s));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public SocieteDto updateSociete(UUID id, UpdateSocieteRequest req) {
        Societe s = require(id);
        if (req.nom() != null) s.setNom(req.nom());
        if (req.siretIce() != null) s.setSiretIce(req.siretIce());
        if (req.adresse() != null) s.setAdresse(req.adresse());
        if (req.emailDpo() != null) s.setEmailDpo(req.emailDpo());
        if (req.logoUrl() != null) s.setLogoUrl(req.logoUrl());
        if (req.pays() != null) s.setPays(req.pays());
        return SocieteDto.from(societeRepository.save(s));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public void deactivateSociete(UUID id) {
        Societe s = require(id);
        s.setActif(false);
        societeRepository.save(s);
    }

    @Transactional
    public AppUserSocieteDto addUserToSociete(UUID societeId, AddUserRequest req) {
        require(societeId);
        AppUserSocieteId pk = new AppUserSocieteId(req.userId(), societeId);
        if (appUserSocieteRepository.existsById(pk)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_IN_SOCIETE");
        }
        AppUserSociete aus = new AppUserSociete(pk, req.role());
        return AppUserSocieteDto.from(appUserSocieteRepository.save(aus));
    }

    @Transactional
    public void removeUserFromSociete(UUID societeId, UUID userId) {
        AppUserSocieteId pk = new AppUserSocieteId(userId, societeId);
        AppUserSociete aus = appUserSocieteRepository.findById(pk)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND"));
        aus.setActif(false);
        appUserSocieteRepository.save(aus);

        // Bump tokenVersion so all existing JWTs for this user are immediately invalidated.
        // JwtAuthenticationFilter checks tokenVersion on every request.
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        user.incrementTokenVersion();
        userRepository.save(user);
    }

    private Societe require(UUID id) {
        return societeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SOCIETE_NOT_FOUND"));
    }
}
