package com.yem.hlm.backend.dashboard.api.dto;

/**
 * Rule-based alert surfaced on the executive dashboard.
 *
 * <p>Alerts are computed on the fly from existing aggregates — no persistence,
 * no notification side-effects. The frontend renders them as a stacked panel
 * with severity-driven colour and an optional CTA route.
 *
 * @param id        stable identifier per rule (e.g. {@code conversion-drop})
 * @param severity  {@link Severity#INFO}, {@link Severity#WARNING} or {@link Severity#CRITICAL}
 * @param category  short tag used for filtering / icon selection
 * @param title     short headline shown in bold
 * @param message   one-line explanation of what triggered the rule
 * @param ctaLabel  optional button label; {@code null} if no CTA
 * @param ctaRoute  optional Angular route the CTA should navigate to
 */
public record AlertDTO(
        String id,
        Severity severity,
        String category,
        String title,
        String message,
        String ctaLabel,
        String ctaRoute
) {

    public enum Severity { INFO, WARNING, CRITICAL }
}
