package dev.gavinfecko.triagedesk.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exactly one line per request: method, path, status, duration and the caller (when known). Runs
 * right after {@link CorrelationIdFilter} so the id is already in the MDC; the user id is picked
 * up from the request attribute {@link MdcUserFilter} sets once security has run. Health probes
 * are skipped because the platform polls them constantly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000;
            Object user = request.getAttribute(MdcUserFilter.USER_ATTRIBUTE);
            String query = request.getQueryString();
            log.info(
                    "{} {}{} -> {} ({} ms){}",
                    request.getMethod(),
                    request.getRequestURI(),
                    query == null ? "" : "?" + query,
                    response.getStatus(),
                    millis,
                    user == null ? "" : " user_id=" + user);
        }
    }
}
