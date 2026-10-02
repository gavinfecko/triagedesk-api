package dev.gavinfecko.triagedesk.common.security;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Who is calling, taken from the verified access token: {@code sub} is the user id, {@code role} the role. */
public record CurrentUser(UUID id, Role role) {

    public static final String ROLE_CLAIM = "role";
    public static final String FAMILY_CLAIM = "fid";

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return new CurrentUser(
                    UUID.fromString(jwt.getName()), Role.valueOf(jwt.getToken().getClaimAsString(ROLE_CLAIM)));
        }
        throw new AuthenticationCredentialsNotFoundException("No access token on this request");
    }

    /** The login session ("family") the caller's access token belongs to. */
    public static UUID sessionFamily() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().hasClaim(FAMILY_CLAIM)) {
            return UUID.fromString(jwt.getToken().getClaimAsString(FAMILY_CLAIM));
        }
        throw new AuthenticationCredentialsNotFoundException("No session family on this access token");
    }

    public boolean is(Role candidate) {
        return role == candidate;
    }

    /** Agents and admins work tickets; requesters only see their own. */
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.AGENT;
    }
}
