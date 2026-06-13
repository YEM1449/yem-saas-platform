package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QuotaService} — plan-level resource quota enforcement.
 * Docker-free (pure Mockito). Covers the branches of each enforce* method:
 * null context → no-op, société not found → no-op, quota unset (null) → no limit,
 * at-or-over limit → throws, under limit → passes.
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    private static final UUID SOC = UUID.randomUUID();

    @Mock SocieteRepository societeRepository;
    @Mock AppUserSocieteRepository appUserSocieteRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock ContactRepository contactRepository;
    @Mock ProjectRepository projectRepository;

    private QuotaService service() {
        return new QuotaService(societeRepository, appUserSocieteRepository,
                propertyRepository, contactRepository, projectRepository);
    }

    private Societe societeWith(Integer maxUsers, Integer maxBiens, Integer maxContacts, Integer maxProjets) {
        Societe s = new Societe("Test Corp", "MA");
        s.setMaxUtilisateurs(maxUsers);
        s.setMaxBiens(maxBiens);
        s.setMaxContacts(maxContacts);
        s.setMaxProjets(maxProjets);
        return s;
    }

    // ── no-op branches ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("null societeId is a no-op")
    void nullSocieteId_noOp() {
        assertThatCode(() -> service().enforceUserQuota(null)).doesNotThrowAnyException();
        verify(societeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("société not found is a no-op")
    void societeNotFound_noOp() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.empty());
        assertThatCode(() -> service().enforceUserQuota(SOC)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unset quota (null) means no limit — count is never queried")
    void nullQuota_noLimit() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(null, null, null, null)));
        assertThatCode(() -> service().enforceUserQuota(SOC)).doesNotThrowAnyException();
        verify(appUserSocieteRepository, never()).countBySocieteIdAndActifTrue(any());
    }

    // ── user quota ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("user quota: at limit → throws")
    void userQuota_atLimit_throws() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(3, null, null, null)));
        when(appUserSocieteRepository.countBySocieteIdAndActifTrue(SOC)).thenReturn(3L);
        assertThatThrownBy(() -> service().enforceUserQuota(SOC))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("user quota: under limit → passes")
    void userQuota_underLimit_passes() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(3, null, null, null)));
        when(appUserSocieteRepository.countBySocieteIdAndActifTrue(SOC)).thenReturn(2L);
        assertThatCode(() -> service().enforceUserQuota(SOC)).doesNotThrowAnyException();
    }

    // ── bien / contact / project quotas ──────────────────────────────────────

    @Test
    @DisplayName("property quota: at limit → throws")
    void bienQuota_atLimit_throws() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(null, 10, null, null)));
        when(propertyRepository.countBySocieteIdAndDeletedAtIsNull(SOC)).thenReturn(10L);
        assertThatThrownBy(() -> service().enforceBienQuota(SOC))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("contact quota: at limit → throws")
    void contactQuota_atLimit_throws() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(null, null, 50, null)));
        when(contactRepository.countBySocieteIdAndDeletedFalse(SOC)).thenReturn(50L);
        assertThatThrownBy(() -> service().enforceContactQuota(SOC))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("project quota: under limit → passes")
    void projectQuota_underLimit_passes() {
        when(societeRepository.findById(SOC)).thenReturn(Optional.of(societeWith(null, null, null, 5)));
        when(projectRepository.countBySocieteId(SOC)).thenReturn(4L);
        assertThatCode(() -> service().enforceProjectQuota(SOC)).doesNotThrowAnyException();
    }
}
