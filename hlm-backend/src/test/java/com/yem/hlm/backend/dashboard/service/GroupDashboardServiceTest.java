package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO.SocieteRow;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GroupDashboardService} (Vue Groupe) — Docker-free (pure Mockito).
 *
 * <p>Focus: membership gating (ADMIN only), context switch/restore around the per-société
 * summarizer calls, inactive-société exclusion, and group totals aggregation.
 */
@ExtendWith(MockitoExtension.class)
class GroupDashboardServiceTest {

    @Mock private AppUserSocieteRepository membershipRepository;
    @Mock private SocieteRepository societeRepository;
    @Mock private GroupSocieteSummarizer summarizer;
    @Mock private SocieteContextHelper societeCtx;

    private GroupDashboardService service;

    private final UUID userId    = UUID.randomUUID();
    private final UUID societeA  = UUID.randomUUID();
    private final UUID societeB  = UUID.randomUUID();
    private final UUID jwtSociete = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GroupDashboardService(membershipRepository, societeRepository, summarizer, societeCtx);
        SocieteContext.setSocieteId(jwtSociete);
        when(societeCtx.requireUserId()).thenReturn(userId);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    private AppUserSociete membership(UUID societeId, String role) {
        return new AppUserSociete(new AppUserSocieteId(userId, societeId), role);
    }

    private Societe societe(UUID id, String nom, boolean actif) {
        // getId()/getNom() are consumed by the (mocked) summarizer, not the service —
        // stub them leniently so strict-stub checking doesn't flag service-only tests.
        Societe s = mock(Societe.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn(nom);
        when(s.isActif()).thenReturn(actif);
        return s;
    }

    private SocieteRow row(UUID societeId, String nom, long vendus, BigDecimal caConfirme) {
        return new SocieteRow(societeId, nom, 10, 5, vendus,
                GroupSocieteSummarizer.absorptionPct(10, 5, vendus),
                caConfirme, new BigDecimal("1000"),
                3, 1,
                new BigDecimal("500"), new BigDecimal("700"), new BigDecimal("200"), 2,
                1, 1);
    }

    @Test
    @DisplayName("403 when the user has no ADMIN membership at all")
    void noAdminMembership_throwsAccessDenied() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "MANAGER"), membership(societeB, "AGENT")));

        assertThatThrownBy(() -> service.getGroupDashboard())
                .isInstanceOf(AccessDeniedException.class);
        verify(summarizer, never()).summarize(any());
    }

    @Test
    @DisplayName("Only ADMIN memberships are summarized — MANAGER société is excluded")
    void nonAdminMembershipsExcluded() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "ADMIN"), membership(societeB, "MANAGER")));
        Societe a = societe(societeA, "Atlas", true);
        when(societeRepository.findById(societeA)).thenReturn(Optional.of(a));
        when(summarizer.summarize(a)).thenReturn(row(societeA, "Atlas", 8, new BigDecimal("3000")));

        GroupDashboardDTO dto = service.getGroupDashboard();

        assertThat(dto.societes()).hasSize(1);
        assertThat(dto.societes().get(0).societeId()).isEqualTo(societeA);
        verify(societeRepository, never()).findById(societeB);
    }

    @Test
    @DisplayName("Inactive sociétés are skipped without failing the whole view")
    void inactiveSocieteSkipped() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "ADMIN"), membership(societeB, "ADMIN")));
        Societe a = societe(societeA, "Atlas", true);
        Societe b = societe(societeB, "Riad", false);
        when(societeRepository.findById(societeA)).thenReturn(Optional.of(a));
        when(societeRepository.findById(societeB)).thenReturn(Optional.of(b));
        when(summarizer.summarize(a)).thenReturn(row(societeA, "Atlas", 8, new BigDecimal("3000")));

        GroupDashboardDTO dto = service.getGroupDashboard();

        assertThat(dto.societes()).hasSize(1);
        assertThat(dto.totals().societesCount()).isEqualTo(1);
        verify(summarizer, never()).summarize(b);
    }

    @Test
    @DisplayName("Totals are summed across sociétés and rows sorted by CA confirmé desc")
    void totalsAggregatedAndSorted() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "ADMIN"), membership(societeB, "ADMIN")));
        Societe a = societe(societeA, "Atlas", true);
        Societe b = societe(societeB, "Riad", true);
        when(societeRepository.findById(societeA)).thenReturn(Optional.of(a));
        when(societeRepository.findById(societeB)).thenReturn(Optional.of(b));
        when(summarizer.summarize(a)).thenReturn(row(societeA, "Atlas", 8, new BigDecimal("3000")));
        when(summarizer.summarize(b)).thenReturn(row(societeB, "Riad", 2, new BigDecimal("9000")));

        GroupDashboardDTO dto = service.getGroupDashboard();

        // Riad (9000) first — best performer leads the comparison.
        assertThat(dto.societes()).extracting(SocieteRow::nom).containsExactly("Riad", "Atlas");
        assertThat(dto.totals().caConfirme()).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(dto.totals().unitsVendus()).isEqualTo(10);
        assertThat(dto.totals().enRetardCount()).isEqualTo(4);
        // Absorption recomputed from summed units (20 dispo + 10 rés + 10 vendus → 25.0 %)
        assertThat(dto.totals().absorptionPct()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("SocieteContext is switched per société and restored to the JWT société")
    void contextSwitchedAndRestored() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "ADMIN")));
        Societe a = societe(societeA, "Atlas", true);
        when(societeRepository.findById(societeA)).thenReturn(Optional.of(a));
        when(summarizer.summarize(a)).thenAnswer(inv -> {
            // The summarizer must run with the context pointing at the target société (RLS).
            assertThat(SocieteContext.getSocieteId()).isEqualTo(societeA);
            return row(societeA, "Atlas", 8, new BigDecimal("3000"));
        });

        service.getGroupDashboard();

        assertThat(SocieteContext.getSocieteId()).isEqualTo(jwtSociete);
    }

    @Test
    @DisplayName("SocieteContext is restored even when a summarizer call fails")
    void contextRestoredOnException() {
        when(membershipRepository.findByIdUserIdAndActifTrue(userId))
                .thenReturn(List.of(membership(societeA, "ADMIN")));
        Societe a = societe(societeA, "Atlas", true);
        when(societeRepository.findById(societeA)).thenReturn(Optional.of(a));
        when(summarizer.summarize(a)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.getGroupDashboard())
                .isInstanceOf(IllegalStateException.class);
        assertThat(SocieteContext.getSocieteId()).isEqualTo(jwtSociete);
    }
}
