package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.identity.application.TokenService;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
class RefreshTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
    }

    MvcTestResult refresh(String token) {
        return mvc.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("refresh_token", token)))
                .exchange();
    }

    @Test
    void refreshRotatesThePairAndTheNewAccessTokenWorks() {
        Session session = api.loginAs(Role.REQUESTER);
        MvcTestResult result = refresh(session.refreshToken());
        assertThat(result).hasStatusOk();
        String newRefresh = Api.read(result).get("refresh_token").asString();
        String newAccess = Api.read(result).get("access_token").asString();
        assertThat(newRefresh).isNotEqualTo(session.refreshToken());
        assertThat(mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .hasStatusOk();
        Integer replaced = jdbc.sql(
                        "select count(*) from refresh_tokens where token_hash = ? and replaced_by is not null")
                .param(TokenService.hash(session.refreshToken()))
                .query(Integer.class)
                .single();
        assertThat(replaced).isEqualTo(1);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamilyAndIsAudited() {
        Session session = api.loginAs(Role.REQUESTER);
        String rotated =
                Api.read(refresh(session.refreshToken())).get("refresh_token").asString();

        MvcTestResult replay = refresh(session.refreshToken());
        assertThat(replay).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(replay).bodyJson().extractingPath("$.type").isEqualTo("/problems/token-reuse");

        MvcTestResult newest = refresh(rotated);
        assertThat(newest).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(newest).bodyJson().extractingPath("$.type").isEqualTo("/problems/token-revoked");

        Integer audited = jdbc.sql(
                        "select count(*) from audit_events where action = 'auth.token_reuse_detected' and user_id = ?")
                .param(session.userId())
                .query(Integer.class)
                .single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void logoutEndsTheSessionSoItsRefreshTokenStopsWorking() {
        Session session = api.loginAs(Role.AGENT);
        assertThat(mvc.post().uri("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult after = refresh(session.refreshToken());
        assertThat(after).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(after).bodyJson().extractingPath("$.type").isEqualTo("/problems/token-revoked");
    }

    @Test
    void logoutNeedsAnAccessToken() {
        assertThat(mvc.post().uri("/api/v1/auth/logout")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anExpiredRefreshTokenIsRefused() {
        UUID user = api.createUser(Role.REQUESTER);
        String value = "expired-" + UUID.randomUUID();
        jdbc.sql("""
                        insert into refresh_tokens (id, user_id, family_id, token_hash, issued_at, expires_at)
                        values (?, ?, ?, ?, ?, ?)""")
                .params(
                        UUID.randomUUID(),
                        user,
                        UUID.randomUUID(),
                        TokenService.hash(value),
                        Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                        Timestamp.from(Instant.parse("2026-01-08T00:00:00Z")))
                .update();
        assertThat(refresh(value))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/token-expired");
    }

    @Test
    void anUnknownTokenOrADeactivatedUserIsRefused() {
        assertThat(refresh("never-issued"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/invalid-token");

        Session session = api.loginAs(Role.REQUESTER);
        jdbc.sql("update users set active = false where id = ?")
                .param(session.userId())
                .update();
        assertThat(refresh(session.refreshToken()))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/invalid-credentials");
    }
}
