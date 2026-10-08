package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
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
import tools.jackson.databind.JsonNode;

@ApiTest
class PasswordChangeTest {

    static final String TEMPORARY = "first day temporary";
    static final String CHOSEN = "my own long passphrase 42";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    String email;
    UUID id;

    /** A user an admin created with a temporary password. */
    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
        Session admin = api.loginAs(Role.ADMIN);
        email = "new-" + UUID.randomUUID() + "@clinic.test";
        MvcTestResult created = mvc.post()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of(
                        "email", email, "display_name", "Nia New", "role", "AGENT", "temporary_password", TEMPORARY)))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        id = UUID.fromString(Api.read(created).get("id").asString());
    }

    JsonNode login(String password) {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("email", email, "password", password)))
                .exchange();
        assertThat(result).hasStatusOk();
        return Api.read(result);
    }

    MvcTestResult get(JsonNode tokens, String path) {
        return mvc.get()
                .uri(path)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokens.get("access_token").asString())
                .exchange();
    }

    MvcTestResult change(JsonNode tokens, String current, String next) {
        return mvc.post()
                .uri("/api/v1/users/me/password")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokens.get("access_token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("current_password", current, "new_password", next)))
                .exchange();
    }

    MvcTestResult refresh(JsonNode tokens) {
        return mvc.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(
                        Map.of("refresh_token", tokens.get("refresh_token").asString())))
                .exchange();
    }

    String hash() {
        return jdbc.sql("select password_hash from users where id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    @Test
    void aTemporaryPasswordAllowsOnlyTheWayToChangeIt() {
        JsonNode session = login(TEMPORARY);
        assertThat(get(session, "/api/v1/tickets"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/password-change-required");
        assertThat(get(session, "/api/v1/users/me")).hasStatusOk();
    }

    @Test
    void changingThePasswordEndsOtherSessionsAndLiftsTheRestriction() {
        JsonNode phone = login(TEMPORARY);
        JsonNode laptop = login(TEMPORARY);

        assertThat(change(laptop, TEMPORARY, CHOSEN)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.sql("select must_change_password from users where id = ?")
                        .param(id)
                        .query(Boolean.class)
                        .single())
                .isFalse();
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'user.password_changed' and user_id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);

        assertThat(refresh(phone)).as("the other session ended").hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult renewed = refresh(laptop);
        assertThat(renewed).as("the session that made the change carries on").hasStatusOk();
        assertThat(get(Api.read(renewed), "/api/v1/tickets")).hasStatusOk();

        login(CHOSEN);
        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Api.json(Map.of("email", email, "password", TEMPORARY))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aWrongCurrentPasswordOrAWeakNewOneChangesNothing() {
        JsonNode session = login(TEMPORARY);
        String before = hash();
        assertThat(change(session, "not my password", CHOSEN))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/invalid-credentials");
        assertThat(change(session, TEMPORARY, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("new_password");
        assertThat(change(session, TEMPORARY, "password1234")).as("breached").hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(change(session, TEMPORARY, TEMPORARY))
                .as("same as the current one")
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("new_password");
        assertThat(hash()).isEqualTo(before);
    }
}
