package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
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
    private final PropertyRepository propertyRepository;
    private final ContactRepository contactRepository;
    private final ProjectRepository projectRepository;

    public QuotaService(SocieteRepository societeRepository,
                        AppUserSocieteRepository appUserSocieteRepository,
                        PropertyRepository propertyRepository,
                        ContactRepository contactRepository,
                        ProjectRepository projectRepository) {
        this.societeRepository = societeRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.propertyRepository = propertyRepository;
        this.contactRepository = contactRepository;
        this.projectRepository = projectRepository;
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
        if (societe == null) return;

        Integer max = societe.getMaxUtilisateurs();
        if (max == null) return;

        long current = appUserSocieteRepository.countBySocieteIdAndActifTrue(societeId);
        if (current >= max) {
            throw new BusinessRuleException(QUOTA_UTILISATEURS_ATTEINT,
                    "La société a atteint son quota d'utilisateurs (" + max + ").");
        }
    }

    /**
     * Verifies that the société has not reached its {@code maxBiens} (property) quota.
     * Call this before creating a new property.
     *
     * @throws BusinessRuleException (QUOTA_BIENS_ATTEINT, 409) when limit is reached.
     */
    public void enforceBienQuota(UUID societeId) {
        if (societeId == null) return;
        Societe societe = societeRepository.findById(societeId).orElse(null);
        if (societe == null) return;

        Integer max = societe.getMaxBiens();
        if (max == null) return;

        long current = propertyRepository.countBySocieteIdAndDeletedAtIsNull(societeId);
        if (current >= max) {
            throw new BusinessRuleException(QUOTA_BIENS_ATTEINT,
                    "La société a atteint son quota de biens (" + max + ").");
        }
    }

    /**
     * Verifies that the société has not reached its {@code maxContacts} quota.
     * Call this before creating a new contact.
     *
     * @throws BusinessRuleException (QUOTA_CONTACTS_ATTEINT, 409) when limit is reached.
     */
    public void enforceContactQuota(UUID societeId) {
        if (societeId == null) return;
        Societe societe = societeRepository.findById(societeId).orElse(null);
        if (societe == null) return;

        Integer max = societe.getMaxContacts();
        if (max == null) return;

        long current = contactRepository.countBySocieteIdAndDeletedFalse(societeId);
        if (current >= max) {
            throw new BusinessRuleException(QUOTA_CONTACTS_ATTEINT,
                    "La société a atteint son quota de contacts (" + max + ").");
        }
    }

    /**
     * Verifies that the société has not reached its {@code maxProjets} quota.
     * Call this before creating a new project.
     *
     * @throws BusinessRuleException (QUOTA_PROJETS_ATTEINT, 409) when limit is reached.
     */
    public void enforceProjectQuota(UUID societeId) {
        if (societeId == null) return;
        Societe societe = societeRepository.findById(societeId).orElse(null);
        if (societe == null) return;

        Integer max = societe.getMaxProjets();
        if (max == null) return;

        long current = projectRepository.countBySocieteId(societeId);
        if (current >= max) {
            throw new BusinessRuleException(QUOTA_PROJETS_ATTEINT,
                    "La société a atteint son quota de projets (" + max + ").");
        }
    }
}
