package com.yem.hlm.backend.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** P5 — the RLS aspect must fail closed (never set the nil-UUID bypass) for a CRM principal with no société. */
class RlsContextAspectTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RlsContextAspect aspect = new RlsContextAspect(jdbc);

    @AfterEach
    void clear() {
        SocieteContext.clear();
    }

    @Test
    void crmPrincipalWithoutSociete_failsClosed_neverBypasses() {
        SocieteContext.setUserId(UUID.randomUUID());
        SocieteContext.setRole("ROLE_AGENT");      // société-scoped principal …
        // … but no société set, and not super-admin → must not reach the nil-UUID bypass

        assertThatThrownBy(aspect::setSocieteIdOnConnection)
                .isInstanceOf(TenantIsolationException.class);
        verify(jdbc, never()).queryForObject(anyString(), eq(String.class), any(Object[].class));
        verify(jdbc, never()).queryForObject(anyString(), eq(String.class), anyString());
    }

    @Test
    void crmPrincipalWithSociete_setsThatSociete() {
        UUID societe = UUID.randomUUID();
        SocieteContext.setUserId(UUID.randomUUID());
        SocieteContext.setRole("ROLE_MANAGER");
        SocieteContext.setSocieteId(societe);

        aspect.setSocieteIdOnConnection();

        verify(jdbc).queryForObject(anyString(), eq(String.class), eq(societe.toString()));
    }

    @Test
    void systemMode_bypassesWithNilUuid() {
        SocieteContext.setSystem();   // scheduler / SUPER_ADMIN → isSuperAdmin()==true, no société

        aspect.setSocieteIdOnConnection();

        verify(jdbc).queryForObject(anyString(), eq(String.class), eq(RlsContextAspect.NIL_UUID));
    }

    @Test
    void unauthenticatedPublicContext_bypassesWithNilUuid() {
        // login / portal magic-link: no role, no société — JPQL societe_id params are the filter
        aspect.setSocieteIdOnConnection();

        assertThat(SocieteContext.getRole()).isNull();
        verify(jdbc).queryForObject(anyString(), eq(String.class), eq(RlsContextAspect.NIL_UUID));
    }
}
