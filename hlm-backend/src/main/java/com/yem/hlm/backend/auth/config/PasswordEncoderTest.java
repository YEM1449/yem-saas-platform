package com.yem.hlm.backend.auth.config;

import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordEncoderTest {
    @Test
    public void bcryt_should_match(){
        var encoder = new BCryptPasswordEncoder();

        String raw = "admin123!";
        String hash = encoder.encode(raw);

        assertTrue(encoder.matches(raw, hash));
        assertFalse(encoder.matches("wrong", hash));
    }
}
