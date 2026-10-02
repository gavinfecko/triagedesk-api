package dev.gavinfecko.triagedesk.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Test helper: creates users of any role straight in the database and logs them in through the API. */
public final class Api {

    public static final String PASSWORD = "correct horse battery";
    // One hash reused for every test user: BCrypt is deliberately slow, and tests create many users.
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public Api(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** A logged-in user: their id and tokens. */
    public record Session(UUID userId, String email, Role role, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public UUID createUser(Role role) {
        return createUser(role, role.name().toLowerCase() + "-" + UUID.randomUUID() + "@clinic.test");
    }

    public UUID createUser(Role role, String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-09-01T12:00:00Z"));
        jdbc.sql("""
                        insert into users (id, email, display_name, password_hash, role, active, created_at, updated_at)
                        values (?, ?, ?, ?, ?, true, ?, ?)""")
                .params(
                        id,
                        email,
                        role.name().charAt(0) + role.name().substring(1).toLowerCase() + " "
                                + id.toString().substring(0, 4),
                        PASSWORD_HASH,
                        role.name(),
                        now,
                        now)
                .update();
        return id;
    }

    public Session loginAs(Role role) {
        UUID id = createUser(role);
        String email = jdbc.sql("select email from users where id = ?")
                .param(id)
                .query(String.class)
                .single();
        return login(id, email, role);
    }

    public Session login(UUID id, String email, Role role) {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(java.util.Map.of("email", email, "password", PASSWORD)))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        JsonNode body = read(result);
        return new Session(
                id,
                email,
                role,
                body.get("access_token").asString(),
                body.get("refresh_token").asString());
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static JsonNode read(MvcTestResult result) {
        try {
            return JSON.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
