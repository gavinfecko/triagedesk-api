package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.application.UserAdminService;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
class UserAdminTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserAdminService service;

    Api api;
    Session admin;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        admin = api.loginAs(Role.ADMIN);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    MvcTestResult as(Session who, String method, String path, Object body) {
        var request = mvc.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(Api.json(body));
        }
        return request.exchange();
    }

    @Test
    void adminCreatesAnAgentWithATemporaryPasswordThatWorks() {
        String email = "new-agent-" + UUID.randomUUID() + "@clinic.test";
        MvcTestResult created = as(
                admin,
                "POST",
                "/api/v1/users",
                Map.of(
                        "email",
                        email,
                        "display_name",
                        "Ana Agent",
                        "role",
                        "AGENT",
                        "temporary_password",
                        "first day temporary"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("AGENT");
        assertThat(created).bodyJson().extractingPath("$.must_change_password").isEqualTo(true);
        String id = Api.read(created).get("id").asString();

        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(Map.of("email", email, "password", "first day temporary"))))
                .hasStatusOk();
        assertThat(auditCount("user.created", UUID.fromString(id), admin.userId()))
                .isEqualTo(1);

        assertThat(as(
                        admin,
                        "POST",
                        "/api/v1/users",
                        Map.of(
                                "email",
                                email,
                                "display_name",
                                "Dup",
                                "role",
                                "AGENT",
                                "temporary_password",
                                "first day temporary")))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void listFiltersByRoleAndActiveAndIsPaged() {
        UUID agent = api.createUser(Role.AGENT);
        MvcTestResult page = as(admin, "GET", "/api/v1/users?role=AGENT&active=true&page=0&size=500", null);
        assertThat(page).hasStatusOk();
        assertThat(page).bodyJson().extractingPath("$.page.size").isEqualTo(100);
        assertThat(page)
                .bodyJson()
                .extractingPath("$.page.total_elements")
                .asNumber()
                .satisfies(n -> assertThat(n.longValue()).isPositive());
        assertThat(page).bodyJson().extractingPath("$.items[*].role").asArray().containsOnly("AGENT");
        assertThat(page).bodyJson().extractingPath("$.items[*].id").asArray().contains(agent.toString());
    }

    @Test
    void roleChangeAndRenameAreAuditedWithBeforeAndAfter() {
        UUID requester = api.createUser(Role.REQUESTER);
        MvcTestResult changed = as(
                admin,
                "PATCH",
                "/api/v1/users/" + requester,
                Map.of("role", "AGENT", "display_name", "Promoted Person"));
        assertThat(changed).hasStatusOk();
        assertThat(changed).bodyJson().extractingPath("$.role").isEqualTo("AGENT");
        String after = jdbc.sql(
                        "select after_value::text from audit_events where action = 'user.role_changed' and user_id = ?")
                .param(requester)
                .query(String.class)
                .single();
        String before = jdbc.sql(
                        "select before_value::text from audit_events where action = 'user.role_changed' and user_id = ?")
                .param(requester)
                .query(String.class)
                .single();
        assertThat(before).isEqualTo("\"REQUESTER\"");
        assertThat(after).isEqualTo("\"AGENT\"");
        assertThat(auditCount("user.renamed", requester, admin.userId())).isEqualTo(1);
        assertThat(as(admin, "PATCH", "/api/v1/users/" + UUID.randomUUID(), Map.of("role", "AGENT")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theLastActiveAdminCannotBeDemotedOrDeactivated() {
        jdbc.sql("update users set active = false where role = 'ADMIN' and id <> ?")
                .param(admin.userId())
                .update();
        MvcTestResult demote = as(admin, "PATCH", "/api/v1/users/" + admin.userId(), Map.of("role", "AGENT"));
        assertThat(demote)
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/last-admin");
        assertThat(as(admin, "POST", "/api/v1/users/" + admin.userId() + "/deactivate", null))
                .hasStatus(HttpStatus.CONFLICT);

        UUID second = api.createUser(Role.ADMIN);
        assertThat(as(admin, "PATCH", "/api/v1/users/" + second, Map.of("role", "AGENT")))
                .hasStatusOk();
    }

    @Test
    void deactivationBlocksLoginEndsSessionsAndKeepsTheRow() {
        Session agent = api.loginAs(Role.AGENT);
        MvcTestResult result = as(admin, "POST", "/api/v1/users/" + agent.userId() + "/deactivate", null);
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.active").isEqualTo(false);
        assertThat(as(admin, "POST", "/api/v1/users/" + agent.userId() + "/deactivate", null))
                .hasStatusOk();

        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(Map.of("email", agent.email(), "password", Api.PASSWORD))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(Map.of("refresh_token", agent.refreshToken()))))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/token-revoked");
        assertThat(auditCount("user.deactivated", agent.userId(), admin.userId()))
                .isEqualTo(1);
    }

    @Test
    void theServiceEnforcesTheAdminRoleEvenWithoutTheController() {
        assertThatThrownBy(() -> service.list(null, null, 0, 10))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        Jwt agentToken = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "AGENT")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        agentToken,
                        java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AGENT"))));
        assertThatThrownBy(() -> service.list(null, null, 0, 10)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deactivate(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    private int auditCount(String action, UUID user, UUID actor) {
        return jdbc.sql("select count(*) from audit_events where action = ? and user_id = ? and actor_id = ?")
                .params(action, user, actor)
                .query(Integer.class)
                .single();
    }
}
