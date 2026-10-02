package dev.gavinfecko.triagedesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.common.security.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static void authenticate(UUID id, Role role, UUID family) {
        Jwt.Builder jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(id.toString())
                .claim("role", role.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (family != null) {
            jwt.claim("fid", family.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    @Test
    void readsIdRoleAndSessionFamilyFromTheAccessToken() {
        UUID id = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        authenticate(id, Role.AGENT, family);
        CurrentUser user = CurrentUser.get();
        assertThat(user.id()).isEqualTo(id);
        assertThat(user.is(Role.AGENT)).isTrue();
        assertThat(user.isStaff()).isTrue();
        assertThat(CurrentUser.sessionFamily()).isEqualTo(family);
    }

    @Test
    void requestersAreNotStaff() {
        authenticate(UUID.randomUUID(), Role.REQUESTER, UUID.randomUUID());
        assertThat(CurrentUser.get().isStaff()).isFalse();
        authenticate(UUID.randomUUID(), Role.ADMIN, UUID.randomUUID());
        assertThat(CurrentUser.get().isStaff()).isTrue();
    }

    @Test
    void noTokenOrNoFamilyIsAnAuthenticationProblem() {
        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));
        assertThatThrownBy(CurrentUser::sessionFamily).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        authenticate(UUID.randomUUID(), Role.REQUESTER, null);
        assertThatThrownBy(CurrentUser::sessionFamily).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
