package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.usermanagement.domain.UserProjectAccess;
import com.yem.hlm.backend.usermanagement.domain.UserQuota;
import com.yem.hlm.backend.usermanagement.dto.ProjectAccessRequest;
import com.yem.hlm.backend.usermanagement.dto.ProjectAccessResponse;
import com.yem.hlm.backend.usermanagement.dto.UserQuotaRequest;
import com.yem.hlm.backend.usermanagement.dto.UserQuotaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserSettingsService {

    private final UserQuotaRepository         quotaRepo;
    private final UserProjectAccessRepository  accessRepo;

    public UserSettingsService(UserQuotaRepository quotaRepo,
                               UserProjectAccessRepository accessRepo) {
        this.quotaRepo  = quotaRepo;
        this.accessRepo = accessRepo;
    }

    // ── Quotas ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserQuotaResponse getQuota(UUID userId, UUID societeId, String month) {
        return quotaRepo.findBySocieteIdAndUserIdAndYearMonth(societeId, userId, month)
                .map(UserQuotaResponse::from)
                .orElse(UserQuotaResponse.empty(userId, month));
    }

    @Transactional
    public UserQuotaResponse upsertQuota(UUID userId, UUID societeId, UserQuotaRequest req) {
        UserQuota quota = quotaRepo
                .findBySocieteIdAndUserIdAndYearMonth(societeId, userId, req.month())
                .orElse(null);
        if (quota == null) {
            quota = new UserQuota(societeId, userId, req.month(), req.caCible(), req.ventesCountCible());
            quotaRepo.save(quota);
        } else {
            quota.update(req.caCible(), req.ventesCountCible());
        }
        return UserQuotaResponse.from(quota);
    }

    // ── Project access ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectAccessResponse getProjectAccess(UUID userId, UUID societeId) {
        List<UUID> projectIds = accessRepo.findProjectIdsBySocieteIdAndUserId(societeId, userId);
        return new ProjectAccessResponse(userId, projectIds);
    }

    @Transactional
    public ProjectAccessResponse setProjectAccess(UUID userId, UUID societeId, ProjectAccessRequest req) {
        accessRepo.deleteBySocieteIdAndUserId(societeId, userId);
        if (req.projectIds() != null) {
            List<UserProjectAccess> entries = req.projectIds().stream()
                    .distinct()
                    .map(pid -> new UserProjectAccess(userId, pid, societeId))
                    .toList();
            if (!entries.isEmpty()) accessRepo.saveAll(entries);
        }
        return getProjectAccess(userId, societeId);
    }
}
