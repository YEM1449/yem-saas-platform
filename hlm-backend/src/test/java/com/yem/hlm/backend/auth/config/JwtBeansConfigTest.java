package com.yem.hlm.backend.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT secret length guardrail (BE-AUTH-12).
 * HS256 requires a secret of at least 32 bytes (256 bits).
 */
class JwtBeansConfigTest {

    private final JwtBeansConfig config = new JwtBeansConfig();

    @Test
    void encoder_rejectsSecretShorterThan32Bytes() {
        var props = new JwtProperties("short-secret", 3600);
        assertThatThrownBy(() -> config.jwtEncoder(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void decoder_rejectsSecretShorterThan32Bytes() {
        var props = new JwtProperties("short-secret", 3600);
        assertThatThrownBy(() -> config.jwtDecoder(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void encoder_acceptsSecretOf32Bytes() {
        var props = new JwtProperties("01234567890123456789012345678901", 3600); // exactly 32
        var encoder = config.jwtEncoder(props);
        assertThat(encoder).isNotNull();
    }

    @Test
    void decoder_acceptsSecretOf32Bytes() {
        var props = new JwtProperties("01234567890123456789012345678901", 3600); // exactly 32
        var decoder = config.jwtDecoder(props);
        assertThat(decoder).isNotNull();
    }

    @Test
    void encoder_rejectsBlankSecret() {
        var props = new JwtProperties("   ", 3600);
        assertThatThrownBy(() -> config.jwtEncoder(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty");
    }
}
