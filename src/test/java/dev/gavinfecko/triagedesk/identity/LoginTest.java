package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
class LoginTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    MeterRegistry meters;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
    }

    MvcTestResult login(String email, String password) {
        return mvc.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(Map.of("email", email, "password", password)))
                .exchange();
    }

    @Test
    void validCredentialsReturnABearerPairAndTheTokenReachesUsersMe() {
        UUID id = api.createUser(Role.AGENT);
        String email = jdbc.sql("select email from users where id = ?")
                .param(id)
                .query(String.class)
                .single();
        MvcTestResult result = login(email.toUpperCase(), Api.PASSWORD);
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.token_type").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expires_in").isEqualTo(900);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.refresh_token")
                .asString()
                .hasSizeGreaterThan(40);

        String access = Api.read(result).get("access_token").asString();
        Jwt jwt = decoder.decode(access);
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("AGENT");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));

        MvcTestResult me = mvc.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
        assertThat(me).bodyJson().extractingPath("$.role").isEqualTo("AGENT");
    }

    @Test
    void wrongPasswordUnknownEmailAndInactiveAccountAllLookIdentical() {
        UUID id = api.createUser(Role.REQUESTER);
        String email = jdbc.sql("select email from users where id = ?")
                .param(id)
                .query(String.class)
                .single();
        double before =
                meters.counter("auth.login.failed", "reason", "bad_credentials").count();

        String wrongPassword = body(login(email, "not the password at all"));
        String unknownEmail = body(login("nobody-" + UUID.randomUUID() + "@clinic.test", Api.PASSWORD));
        jdbc.sql("update users set active = false where id = ?").param(id).update();
        String inactive = body(login(email, Api.PASSWORD));

        assertThat(wrongPassword).contains("/problems/invalid-credentials");
        assertThat(withoutCorrelation(unknownEmail)).isEqualTo(withoutCorrelation(wrongPassword));
        assertThat(withoutCorrelation(inactive)).isEqualTo(withoutCorrelation(wrongPassword));
        assertThat(meters.counter("auth.login.failed", "reason", "bad_credentials")
                        .count())
                .isEqualTo(before + 3);
    }

    private String body(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String withoutCorrelation(String body) {
        return body.replaceAll("\"correlation_id\":\"[^\"]+\"", "");
    }

    @Test
    void expiredMalformedAndForeignTokensAreRejectedAndCounted() {
        Session session = api.loginAs(Role.REQUESTER);
        double before =
                meters.counter("auth.login.failed", "reason", "invalid_token").count();

        String expired = sign(
                "test-only-signing-key-not-used-anywhere-else-000",
                session.userId(),
                Instant.parse("2026-01-01T00:00:00Z"));
        String foreign = sign(
                "some-other-service-key-that-we-do-not-trust-00",
                session.userId(),
                Instant.now().plusSeconds(600));

        for (String token : new String[] {expired, foreign, "not-a-jwt"}) {
            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange();
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/unauthenticated");
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .startsWith("Bearer");
        }
        assertThat(meters.counter("auth.login.failed", "reason", "invalid_token")
                        .count())
                .isEqualTo(before + 3);
    }

    @Test
    void usersMeWithoutATokenIs401() {
        assertThat(mvc.get().uri("/api/v1/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static String sign(String secret, UUID subject, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("triagedesk")
                .subject(subject.toString())
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .claim("role", "REQUESTER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
