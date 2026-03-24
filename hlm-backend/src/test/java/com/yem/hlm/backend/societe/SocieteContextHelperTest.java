package com.yem.hlm.backend.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SocieteContextHelperTest {

    private final SocieteContextHelper helper = new SocieteContextHelper();

    @AfterEach
    void cleanup() {
        SocieteContext.clear();
    }

    @Test
    void requireSocieteId_returnsId_whenSet() {
        UUID id = UUID.randomUUID();
        SocieteContext.setSocieteId(id);
        assertThat(helper.requireSocieteId()).isEqualTo(id);
    }

    @Test
    void requireSocieteId_throws_whenNull() {
        assertThatThrownBy(helper::requireSocieteId)
                .isInstanceOf(CrossSocieteAccessException.class)
                .hasMessageContaining("société");
    }

    @Test
    void requireUserId_throws_whenNull() {
        assertThatThrownBy(helper::requireUserId)
                .isInstanceOf(CrossSocieteAccessException.class);
    }

    @Test
    void requireUserId_returnsId_whenSet() {
        UUID id = UUID.randomUUID();
        SocieteContext.setUserId(id);
        assertThat(helper.requireUserId()).isEqualTo(id);
    }

    @Test
    void runAsSystem_setsAndClearsContext() {
        SocieteContext.setSocieteId(UUID.randomUUID());
        helper.runAsSystem(() -> {
            assertThat(SocieteContext.isSuperAdmin()).isTrue();
            assertThat(SocieteContext.getSocieteId()).isNull();
        });
        assertThat(SocieteContext.getSocieteId()).isNull();
        assertThat(SocieteContext.isSuperAdmin()).isFalse();
    }

    @Test
    void runAsSystem_clearsContextEvenOnException() {
        SocieteContext.setSocieteId(UUID.randomUUID());
        assertThatThrownBy(() -> helper.runAsSystem(() -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(RuntimeException.class);
        assertThat(SocieteContext.getSocieteId()).isNull();
        assertThat(SocieteContext.isSuperAdmin()).isFalse();
    }
}
