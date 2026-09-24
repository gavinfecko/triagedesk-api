package dev.gavinfecko.triagedesk.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id that travels through the logs (MDC), the response header and every
 * error body. A caller may supply one in {@code X-Correlation-Id}; anything unsafe or missing is
 * replaced with a fresh UUID. Runs before the security filter chain so 401s carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation_id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = incoming(request);
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The id of the request being handled on this thread, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    private static String incoming(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        return header != null && SAFE.matcher(header).matches() ? header : UUID.randomUUID().toString();
    }
}
