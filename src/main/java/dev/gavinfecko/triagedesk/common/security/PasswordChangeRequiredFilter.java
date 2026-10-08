package dev.gavinfecko.triagedesk.common.security;

import dev.gavinfecko.triagedesk.common.errors.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Someone signed in with an admin-issued temporary password may only look at themselves, change the password,
 * refresh or log out; anything else is {@code 403 password-change-required} (TD-113). The flag travels in the
 * access token, so this costs no database read.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED =
            Set.of("/api/v1/users/me", "/api/v1/users/me/password", "/api/v1/auth/logout", "/api/v1/auth/refresh");

    private final HandlerExceptionResolver resolver;

    PasswordChangeRequiredFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean mustChange = auth instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(CurrentUser.PASSWORD_CHANGE_CLAIM));
        if (mustChange && !ALLOWED.contains(request.getRequestURI())) {
            resolver.resolveException(
                    request,
                    response,
                    null,
                    new ApiException(
                            HttpStatus.FORBIDDEN,
                            "password-change-required",
                            "Password change required",
                            "Your account has a temporary password. Change it with POST /api/v1/users/me/password, "
                                    + "then refresh your token."));
            return;
        }
        chain.doFilter(request, response);
    }
}
