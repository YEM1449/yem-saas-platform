package com.yem.hlm.backend.legal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a market-aware {@link Clock} so all legal date/time math is computed in the active
 * market's jurisdiction zone ({@link MarketConfig#getZoneId()}) rather than the JVM default.
 *
 * <p>Business code should obtain "now" via this injected {@code Clock}
 * ({@code LocalDate.now(clock)}, {@code LocalDateTime.now(clock)}, {@code Instant.now(clock)})
 * instead of the zero-arg {@code now()} calls — this both fixes the zone (EX-009) and makes the
 * services deterministically testable with a fixed clock.
 */
@Configuration
public class MarketTimeConfig {

    @Bean
    public Clock marketClock(MarketConfig marketConfig) {
        return Clock.system(marketConfig.getZoneId());
    }
}
