package com.yem.hlm.backend.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for auth/public endpoint rate limits.
 *
 * <p>Prefix: {@code app.rate-limit}.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    @Valid
    private Limit login = new Limit();
    @Valid
    private Limit portalLink = new Limit();

    public RateLimitProperties() {
        login.setCapacity(5);
        login.setRefillPeriod(Duration.ofMinutes(15));
        login.setExceededMessage("Too many login attempts. Please try again in 15 minutes.");

        portalLink.setCapacity(3);
        portalLink.setRefillPeriod(Duration.ofHours(1));
        portalLink.setExceededMessage("Too many magic link requests. Please try again in 1 hour.");
    }

    public Limit getLogin() {
        return login;
    }

    public void setLogin(Limit login) {
        this.login = login;
    }

    public Limit getPortalLink() {
        return portalLink;
    }

    public void setPortalLink(Limit portalLink) {
        this.portalLink = portalLink;
    }

    public static class Limit {
        @Min(1)
        private int capacity = 1;

        @NotNull
        private Duration refillPeriod = Duration.ofMinutes(1);

        @NotBlank
        private String exceededMessage = "Too many requests. Please try again later.";

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        public String getExceededMessage() {
            return exceededMessage;
        }

        public void setExceededMessage(String exceededMessage) {
            this.exceededMessage = exceededMessage;
        }
    }
}
