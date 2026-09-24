package dev.gavinfecko.triagedesk.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs just after the security filter chain and puts the authenticated caller into the MDC
 * ({@code user_id}) for every log line of the request, and onto the request for the access log.
 */
@Component
@Order(MdcUserFilter.AFTER_SECURITY)
public class MdcUserFilter extends OncePerRequestFilter {

    /** Spring Boot registers the security filter chain at -100; this runs right after it. */
    static final int AFTER_SECURITY = -99;

    public static final String MDC_KEY = "user_id";
    static final String USER_ATTRIBUTE = MdcUserFilter.class.getName() + ".user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean known = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        if (known) {
            MDC.put(MDC_KEY, auth.getName());
            request.setAttribute(USER_ATTRIBUTE, auth.getName());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
