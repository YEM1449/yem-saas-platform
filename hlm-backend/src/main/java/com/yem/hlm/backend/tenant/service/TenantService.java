package com.yem.hlm.backend.tenant.service;

import com.yem.hlm.backend.tenant.api.dto.OwnerResponse;
import com.yem.hlm.backend.tenant.api.dto.TenantCreateRequest;
import com.yem.hlm.backend.tenant.api.dto.TenantResponse;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class TenantService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantResponse createTenantWithOwner(TenantCreateRequest request) {
        String normalizedKey = normalizeKey(request.key());
        Tenant tenant;
        User owner;
        try {
            if (tenantRepository.existsByKey(normalizedKey)) {
                throw new TenantKeyAlreadyExistsException(normalizedKey);
            }
            tenant = tenantRepository.save(new Tenant(normalizedKey, request.name()));
            owner = new User(
                    tenant,
                    request.ownerEmail(),
                    passwordEncoder.encode(request.ownerPassword())
            );
            owner = userRepository.save(owner);
        } catch (DataIntegrityViolationException ex) {
            throw new TenantKeyAlreadyExistsException(normalizedKey);
        }

        return toResponse(tenant, owner);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (contextTenantId == null || !contextTenantId.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        User owner = userRepository.findFirstByTenant_IdOrderByEmailAsc(tenantId).orElse(null);
        return toResponse(tenant, owner);
    }

    private TenantResponse toResponse(Tenant tenant, User owner) {
        TenantResponse.TenantInfo tenantInfo = new TenantResponse.TenantInfo(
                tenant.getId(),
                tenant.getKey(),
                tenant.getName()
        );
        OwnerResponse ownerResponse = owner == null
                ? null
                : new OwnerResponse(owner.getId(), owner.getEmail(), owner.isEnabled());

        return new TenantResponse(tenantInfo, ownerResponse);
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
