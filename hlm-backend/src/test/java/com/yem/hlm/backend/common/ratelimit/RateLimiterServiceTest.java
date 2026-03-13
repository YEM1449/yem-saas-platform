package com.yem.hlm.backend.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterServiceTest {

    @Test
    void loginRateLimit_usesConfiguredCapacityAndMessage() {
        RateLimitProperties props = new RateLimitProperties();
        props.getLogin().setCapacity(2);
        props.getLogin().setRefillPeriod(Duration.ofHours(1));
        props.getLogin().setExceededMessage("login limit reached");

        RateLimiterService service = new RateLimiterService(props);

        assertDoesNotThrow(() -> service.checkLogin("user@test.com"));
        assertDoesNotThrow(() -> service.checkLogin("user@test.com"));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkLogin("user@test.com")
        );
        assertEquals("login limit reached", ex.getMessage());
    }

    @Test
    void portalRateLimit_usesDedicatedBucket() {
        RateLimitProperties props = new RateLimitProperties();
        props.getPortalLink().setCapacity(1);
        props.getPortalLink().setRefillPeriod(Duration.ofHours(1));

        RateLimiterService service = new RateLimiterService(props);

        assertDoesNotThrow(() -> service.checkLogin("same@email.com"));
        assertDoesNotThrow(() -> service.checkPortalLink("same@email.com"));
        assertThrows(RateLimitExceededException.class, () -> service.checkPortalLink("same@email.com"));
    }
}
