package com.yem.hlm.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC with a per-request correlation ID and (after JWT processing) societeId.
 *
 * <p>Reads {@code X-Request-Id} from the incoming request or generates a UUID.
 * The value is also echoed back as {@code X-Request-Id} in the response so callers
 * can correlate log entries. MDC is cleared in the finally block to prevent leaking
 * between threads in the container pool.
 *
 * <p>Runs before {@link com.yem.hlm.backend.auth.security.JwtAuthenticationFilter}
 * so that the requestId is present in all log statements including auth failures.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_SOCIETE_ID = "societeId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_SOCIETE_ID);
        }
    }
}
