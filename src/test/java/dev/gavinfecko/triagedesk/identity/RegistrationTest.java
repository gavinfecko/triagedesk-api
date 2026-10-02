package dev.gavinfecko.triagedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gavinfecko.triagedesk.support.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
class RegistrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    static String uniqueEmail() {
        return "staff-" + UUID.randomUUID() + "@clinic.test";
    }

    MvcTestResult register(String email, String displayName, String password) {
        return mvc.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","display_name":"%s","password":"%s"}""".formatted(email, displayName, password))
                .exchange();
    }

    @Test
    void validRegistrationCreatesARequesterAndNeverReturnsThePassword() {
        String email = uniqueEmail();
        MvcTestResult result = register(email, "Rosa Front Desk", "correct horse battery");
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Location")).startsWith("/api/v1/users/");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email);
        assertThat(result).bodyJson().extractingPath("$.display_name").isEqualTo("Rosa Front Desk");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("REQUESTER");
        assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(result).bodyText().doesNotContain("password").doesNotContain("correct horse");
    }

    @Test
    void emailIsStoredLowerCaseAndTakenCaseInsensitively() {
        String email = uniqueEmail();
        assertThat(register(email.toUpperCase(), "Nurse Kim", "correct horse battery"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo(email);
        MvcTestResult again = register(email, "Someone Else", "another long passphrase");
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(again).bodyJson().extractingPath("$.type").isEqualTo("/problems/email-taken");
    }

    @Test
    void shortPasswordIsAValidationProblemNamingThePasswordField() {
        MvcTestResult result = register(uniqueEmail(), "Billing Desk", "short1!");
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
    }

    @Test
    void breachedPasswordIsRejectedEvenWhenLongEnough() {
        MvcTestResult result = register(uniqueEmail(), "Billing Desk", "Password123!");
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].message")
                .asString()
                .contains("breached");
    }

    @Test
    void fieldErrorsUseTheJsonNames() {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"correct horse battery\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("email", "display_name");
    }

    @Test
    void passwordIsStoredAsBcryptCost12AndTheRegistrationIsAudited() {
        String email = uniqueEmail();
        assertThat(register(email, "Dr Patel", "correct horse battery")).hasStatus(HttpStatus.CREATED);
        String hash = jdbc.sql("select password_hash from users where email = ?")
                .param(email)
                .query(String.class)
                .single();
        assertThat(hash).matches("^\\$2[aby]\\$12\\$.{53}$");
        Integer audits = jdbc.sql("""
                        select count(*) from audit_events a join users u on u.id = a.user_id
                        where u.email = ? and a.action = 'user.registered' and a.actor_id = u.id""").param(email).query(Integer.class).single();
        assertThat(audits).isEqualTo(1);
    }
}
