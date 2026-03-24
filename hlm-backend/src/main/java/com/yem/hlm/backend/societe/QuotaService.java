package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.*;

/**
 * Enforces plan-level resource quotas for a société.
 *
 * <p>Quota fields on {@link Societe} are nullable — {@code null} means no limit.
 * When a quota is set, the corresponding count is compared and a
 * {@link BusinessRuleException} with a 409-mapped code is thrown if exceeded.
 */
@Service
public class QuotaService {

    private final SocieteRepository societeRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;

    public QuotaService(SocieteRepository societeRepository,
                        AppUserSocieteRepository appUserSocieteRepository) {
        this.societeRepository = societeRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
    }

    /**
     * Verifies that the société has not reached its {@code maxUtilisateurs} quota.
     * Call this before adding a new active member.
     *
     * @throws BusinessRuleException (QUOTA_UTILISATEURS_ATTEINT, 409) when limit is reached.
     */
    public void enforceUserQuota(UUID societeId) {
        if (societeId == null) return;
        Societe societe = societeRepository.findById(societeId).orElse(null);
        if (societe == null) return; // Defensive: societe not found is handled elsewhere

        Integer max = societe.getMaxUtilisateurs();
        if (max == null) return; // No limit configured

        long current = appUserSocieteRepository.countBySocieteIdAndActifTrue(societeId);
        if (current >= max) {
            throw new BusinessRuleException(QUOTA_UTILISATEURS_ATTEINT,
                    "La société a atteint son quota d'utilisateurs (" + max + ").");
        }
    }
}
