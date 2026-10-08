package dev.gavinfecko.triagedesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The role matrix from docs/ARCHITECTURE.md §7, executed. Every protected endpoint appears once with
 * the roles allowed to call it; the test checks each role both ways and an anonymous caller.
 * "Allowed" means the request got past authorization (any status other than 401/403); the endpoint's
 * own behaviour is tested in its story's test class. New endpoints add a row here.
 */
@ApiTest
class AuthorizationMatrixTest {

    record Endpoint(HttpMethod method, String path, String body, Set<Role> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static final Set<Role> ANY = EnumSet.allOf(Role.class);
    static final Set<Role> ADMIN = EnumSet.of(Role.ADMIN);
    static final Set<Role> STAFF = EnumSet.of(Role.ADMIN, Role.AGENT);

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/v1/users/me", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/users", null, ADMIN),
                new Endpoint(HttpMethod.POST, "/api/v1/users", "{}", ADMIN),
                new Endpoint(HttpMethod.PATCH, "/api/v1/users/" + UUID.randomUUID(), "{}", ADMIN),
                new Endpoint(HttpMethod.POST, "/api/v1/users/" + UUID.randomUUID() + "/deactivate", null, ADMIN),
                new Endpoint(HttpMethod.POST, "/api/v1/auth/logout", null, ANY),
                new Endpoint(HttpMethod.POST, "/api/v1/tickets", "{}", ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/tickets", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/tickets/HD-999999", null, ANY),
                new Endpoint(HttpMethod.PATCH, "/api/v1/tickets/HD-999999", "{}", ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/tickets/HD-999999/comments", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/tickets/HD-999999/audit", null, ANY),
                new Endpoint(HttpMethod.POST, "/api/v1/tickets/HD-999999/comments", "{}", ANY),
                new Endpoint(HttpMethod.POST, "/api/v1/tickets/HD-999999/transitions", "{}", ANY),
                new Endpoint(HttpMethod.POST, "/api/v1/tickets/HD-999999/assign", "{}", STAFF),
                new Endpoint(HttpMethod.POST, "/api/v1/tickets/HD-999999/queue", "{}", STAFF),
                new Endpoint(HttpMethod.GET, "/api/v1/categories", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/queues", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/tags", null, ANY),
                new Endpoint(HttpMethod.GET, "/api/v1/sla/calendars", null, STAFF),
                new Endpoint(HttpMethod.PUT, "/api/v1/sla/calendars/" + UUID.randomUUID(), "{}", ADMIN),
                new Endpoint(HttpMethod.PUT, "/api/v1/tickets/HD-999999/tags", "[]", STAFF),
                new Endpoint(HttpMethod.GET, "/actuator/info", null, ADMIN),
                new Endpoint(HttpMethod.GET, "/actuator/metrics", null, ADMIN),
                new Endpoint(HttpMethod.GET, "/v3/api-docs", null, ADMIN));
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Map<Role, Session> sessions;

    @BeforeEach
    void loginEveryRole() {
        Api api = new Api(mvc, jdbc);
        sessions = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            sessions.put(role, api.loginAs(role));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void eachRoleIsAllowedOrForbiddenAsTheMatrixSays(Endpoint endpoint) {
        for (Role role : Role.values()) {
            int status =
                    call(endpoint, sessions.get(role).bearer()).getResponse().getStatus();
            if (endpoint.allowed().contains(role)) {
                assertThat(status).as("%s as %s", endpoint, role).isNotIn(401, 403);
            } else {
                assertThat(status).as("%s as %s", endpoint, role).isEqualTo(403);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anonymousCallersGet401(Endpoint endpoint) {
        assertThat(call(endpoint, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    static Stream<String> publicPosts() {
        return Stream.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh");
    }

    @ParameterizedTest
    @MethodSource("publicPosts")
    void authEndpointsAreReachableWithoutAToken(String path) {
        int status = mvc.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange()
                .getResponse()
                .getStatus();
        assertThat(status).as(path).isEqualTo(400); // reached validation, so authorization let it through
    }

    private MvcTestResult call(Endpoint endpoint, String bearer) {
        var request = mvc.method(endpoint.method()).uri(endpoint.path());
        if (bearer != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        if (endpoint.body() != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return request.exchange();
    }

    static List<Role> roles() {
        return List.of(Role.values());
    }
}
